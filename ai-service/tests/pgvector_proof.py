"""Real PostgreSQL vector/isolation checks invoked only from the isolated proof stack."""
import sys
from uuid import UUID
import psycopg
from app.rag import Repository, RagService
from tests.test_rag import FakeProvider


def main():
    project, document, other = map(UUID, sys.argv[1:4])
    repository = Repository(); provider = FakeProvider(); service = RagService(provider, repository)
    vector = '[' + ','.join(['1'] + ['0']*767) + ']'
    rows = repository.retrieve(project, provider.embedding_model, vector, limit=1)
    assert len(rows) == 1 and rows[0]['project_id'] == project
    with repository.connect() as db:
        a = db.execute('SELECT MIN(embedding <=> %s::vector) AS distance FROM document_chunks WHERE project_id=%s', (vector, project)).fetchone()['distance']
        b = db.execute('SELECT MIN(embedding <=> %s::vector) AS distance FROM document_chunks WHERE project_id=%s', (vector, other)).fetchone()['distance']
        assert b < a, 'Other-project chunks must be closer to exercise filtering before top-k'
        db.execute("UPDATE documents SET processing_status='FAILED' WHERE project_id=%s AND id=%s", (project, document))
    try:
        assert repository.retrieve(project, provider.embedding_model, vector) == []
    finally:
        with repository.connect() as db:
            db.execute("UPDATE documents SET processing_status='READY' WHERE project_id=%s AND id=%s", (project, document))
    assert repository.retrieve(project, 'different-embedding-model', vector) == []
    with repository.connect() as db:
        row = db.execute('SELECT * FROM document_chunks WHERE project_id=%s LIMIT 1', (project,)).fetchone()
        try:
            with db.transaction():
                db.execute('UPDATE document_chunks SET project_id=%s WHERE id=%s', (other, row['id']))
        except psycopg.IntegrityError:
            pass
        else:
            raise AssertionError('Composite document/project foreign key failed')
        try:
            with db.transaction():
                db.execute("UPDATE document_chunks SET embedding='[1,0,0]'::vector WHERE id=%s", (row['id'],))
        except psycopg.DataError:
            pass
        else:
            raise AssertionError('Vector dimension must be 768')
    print('PostgreSQL proof passed: WHERE before top-1 despite closer foreign chunks; READY/model filters; composite FK and vector(768).')


if __name__ == '__main__': main()
