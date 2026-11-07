"""Project-scoped ingestion and exact pgvector retrieval, independent of prompt instructions."""
import math
import os
import re
import time
from uuid import uuid4
import psycopg
from psycopg.rows import dict_row
from app.provider import AiError, configured_provider, normalize

RETRIEVAL_SQL = '''
SELECT c.id, c.project_id, c.document_id, c.page_number, c.content, d.original_filename,
       c.embedding <=> %s::vector AS distance
FROM document_chunks c JOIN documents d ON d.id=c.document_id AND d.project_id=c.project_id
WHERE c.project_id=%s AND d.project_id=%s AND d.processing_status='READY'
  AND c.embedding_model=%s AND c.embedding_dimension=768
ORDER BY c.embedding <=> %s::vector, c.id
LIMIT %s
'''
INSUFFICIENT = 'The project documents do not contain enough relevant information to answer this question.'


def chunks(pages):
    result = []
    for page in pages:
        text = re.sub(r'\s+', ' ', page['content']).strip()
        start = 0
        while start < len(text):
            content = text[start:start + 1200]
            result.append({'page': page['page_number'], 'content': content})
            if len(result) > 800:
                raise AiError('AI_DOCUMENT_TOO_LARGE')
            if start + 1200 >= len(text):
                break
            start += 1000
    return result


def vector_literal(values):
    return '[' + ','.join(format(v, '.12g') for v in normalize(values)) + ']'


class Repository:
    def connect(self):
        return psycopg.connect(host=os.getenv('POSTGRES_HOST', 'postgres'), dbname=os.getenv('POSTGRES_DB', 'structiq'),
            user=os.getenv('POSTGRES_USER', 'structiq'), password=os.getenv('POSTGRES_PASSWORD', ''),
            connect_timeout=3, row_factory=dict_row, options='-c statement_timeout=5000 -c lock_timeout=5000')

    def retrieve(self, project, model, vector, limit=6):
        with self.connect() as db:
            return db.execute(RETRIEVAL_SQL, (vector, project, project, model, vector, limit)).fetchall()


class RagService:
    def __init__(self, provider=None, repository=None):
        self.provider = provider or configured_provider()
        self.repository = repository or Repository()
        if int(os.getenv('EMBEDDING_DIMENSION', '768')) != 768:
            raise ValueError('Embedding dimension must match vector(768)')
        self.model = self.provider.embedding_model

    def configured(self):
        return self.provider.configured()

    def index(self, project, document):
        if not self.configured():
            raise AiError('AI_NOT_CONFIGURED')
        # Document is locked across embedding to serialize replacement/deletion. Spring holds the project lock.
        with self.repository.connect() as db:
            found = db.execute('SELECT id FROM documents WHERE project_id=%s AND id=%s FOR UPDATE', (project, document)).fetchone()
            if not found:
                raise AiError('NOT_FOUND', 404)
            pages = db.execute('SELECT page_number,content FROM document_pages WHERE project_id=%s AND document_id=%s ORDER BY page_number', (project, document)).fetchall()
            parts = chunks(pages)
            if not parts:
                raise AiError('NO_TEXT', 409)
            vectors = []
            deadline = time.monotonic() + 45
            for start in range(0, len(parts), 32):
                if time.monotonic() > deadline:
                    raise AiError('AI_PROVIDER_TIMEOUT')
                vectors.extend(self.provider.embed([p['content'] for p in parts[start:start+32]]))
            if len(vectors) != len(parts):
                raise AiError('AI_INVALID_EMBEDDING')
            db.execute('DELETE FROM document_chunks WHERE project_id=%s AND document_id=%s', (project, document))
            with db.cursor() as cursor:
                cursor.executemany('''INSERT INTO document_chunks(id,project_id,document_id,chunk_index,page_number,content,embedding_model,embedding_dimension,embedding)
                    VALUES (%s,%s,%s,%s,%s,%s,%s,768,%s::vector)''',
                    [(uuid4(), project, document, i, part['page'], part['content'], self.model, vector_literal(vector))
                     for i, (part, vector) in enumerate(zip(parts, vectors))])
            db.execute("UPDATE documents SET processing_status='READY',processing_code='INDEXED',updated_at=CURRENT_TIMESTAMP WHERE project_id=%s AND id=%s", (project, document))
        return {'indexedChunks': len(parts)}

    def ask(self, project, question):
        if not self.configured():
            raise AiError('AI_NOT_CONFIGURED')
        vector = vector_literal(self.provider.embed([question], query=True)[0])
        retrieved = self.repository.retrieve(project, self.model, vector)
        # Defense in depth; SQL, not this check, establishes scope before top-k.
        if any(str(row['project_id']) != str(project) for row in retrieved):
            raise AiError('AI_INVALID_RESPONSE')
        relevant = [row for row in retrieved if math.isfinite(row['distance']) and row['distance'] <= 0.35]
        if not relevant:
            return {'answer': INSUFFICIENT, 'citations': [], 'retrievalCount': 0}
        contexts = [{'source': i+1, 'text': row['content']} for i, row in enumerate(relevant)]
        response = self.provider.generate(question, contexts)
        if not isinstance(response, dict) or not isinstance(response.get('answer'), str) or not isinstance(response.get('sources'), list):
            raise AiError('AI_INVALID_RESPONSE')
        indices = response['sources']
        if len(response['answer']) > 8000 or len(indices) > 6 or any(type(i) is not int or not 1 <= i <= len(relevant) for i in indices):
            raise AiError('AI_INVALID_RESPONSE')
        if not indices or not response['answer'].strip():
            return {'answer': INSUFFICIENT, 'citations': [], 'retrievalCount': len(relevant)}
        citations = []
        for index in dict.fromkeys(indices):
            row = relevant[index-1]
            citations.append({'chunkId': str(row['id']), 'documentId': str(row['document_id']),
                'filename': row['original_filename'], 'pageNumber': row['page_number'], 'excerpt': row['content'][:200]})
        return {'answer': response['answer'].strip(), 'citations': citations, 'retrievalCount': len(relevant)}
