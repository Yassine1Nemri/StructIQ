CREATE TABLE document_chunks (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    document_id UUID NOT NULL,
    chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0),
    page_number INTEGER NOT NULL CHECK (page_number > 0),
    content VARCHAR(1200) NOT NULL,
    embedding_model VARCHAR(100) NOT NULL,
    embedding_dimension INTEGER NOT NULL CHECK (embedding_dimension = 768),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(project_id,document_id) REFERENCES documents(project_id,id) ON DELETE CASCADE,
    UNIQUE(document_id,chunk_index)
);
CREATE INDEX document_chunks_project_document ON document_chunks(project_id,document_id);
