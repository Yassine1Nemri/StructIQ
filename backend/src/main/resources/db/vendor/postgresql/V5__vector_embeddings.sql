CREATE EXTENSION IF NOT EXISTS vector;
ALTER TABLE document_chunks ADD COLUMN embedding vector(768) NOT NULL;
-- Exact project-filtered search. No global approximate index.
