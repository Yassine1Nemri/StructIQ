CREATE TABLE documents (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    uploaded_by UUID NOT NULL REFERENCES users(id),
    original_filename VARCHAR(180) NOT NULL,
    storage_key VARCHAR(100) NOT NULL UNIQUE,
    mime_type VARCHAR(40) NOT NULL CHECK (mime_type IN ('application/pdf','text/plain')),
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    checksum_sha256 VARCHAR(64) NOT NULL,
    processing_status VARCHAR(20) NOT NULL CHECK (processing_status IN ('UPLOADED','PROCESSING','READY','FAILED')),
    processing_code VARCHAR(50) NOT NULL,
    extracted_pages INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(project_id,id)
);
-- Private extracted data, reserved for ingestion. Never returned by metadata endpoints.
CREATE TABLE document_pages (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    document_id UUID NOT NULL,
    page_number INTEGER NOT NULL CHECK (page_number > 0),
    content TEXT NOT NULL,
    FOREIGN KEY(project_id,document_id) REFERENCES documents(project_id,id) ON DELETE CASCADE,
    UNIQUE(document_id,page_number)
);
CREATE INDEX documents_project_created ON documents(project_id,created_at);
CREATE INDEX document_pages_project_document ON document_pages(project_id,document_id);
-- Transactional deletion outbox; rows survive restarts until private bytes are removed.
CREATE TABLE pending_file_deletions (
    storage_key VARCHAR(100) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
