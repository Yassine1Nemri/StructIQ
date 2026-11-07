package dev.structiq.document;

import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {
    public record Metadata(UUID id,UUID projectId,String filename,String mimeType,long sizeBytes,String processingStatus,
        String processingCode,int extractedPages,UUID uploadedBy,OffsetDateTime createdAt) {}
    public record Stored(Metadata metadata,String storageKey) {}
    @io.swagger.v3.oas.annotations.media.Schema(name="DocumentPage")
    public record Page(List<Metadata> items,int page,int size,boolean hasNext,long maxUploadBytes) {}
    private final JdbcTemplate db;
    public DocumentRepository(JdbcTemplate db) { this.db=db; }
    private static final RowMapper<Stored> MAPPER=(r,n) -> new Stored(new Metadata(r.getObject("id",UUID.class),
        r.getObject("project_id",UUID.class),r.getString("original_filename"),r.getString("mime_type"),r.getLong("size_bytes"),
        r.getString("processing_status"),r.getString("processing_code"),r.getInt("extracted_pages"),
        r.getObject("uploaded_by",UUID.class),r.getObject("created_at",OffsetDateTime.class)),r.getString("storage_key"));
    public Optional<Stored> find(UUID project,UUID id) {
        return db.query("SELECT * FROM documents WHERE project_id=? AND id=?",MAPPER,project,id).stream().findFirst();
    }
    public List<Metadata> list(UUID project,int limit,int offset) {
        return db.query("SELECT * FROM documents WHERE project_id=? ORDER BY created_at DESC,id LIMIT ? OFFSET ?",MAPPER,project,limit,offset)
            .stream().map(Stored::metadata).toList();
    }
    public void insert(UUID id,UUID project,UUID user,UploadValidator.Validated upload,String key,String checksum) {
        db.update("INSERT INTO documents(id,project_id,uploaded_by,original_filename,storage_key,mime_type,size_bytes,checksum_sha256,processing_status,processing_code) VALUES (?,?,?,?,?,?,?,?,?,?)",
            id,project,user,upload.filename(),key,upload.mimeType(),upload.bytes().length,checksum,"PROCESSING","EXTRACTING_TEXT");
    }
    public void finish(UUID project,UUID id,ExtractionClient.Result result) {
        db.update("DELETE FROM document_pages WHERE project_id=? AND document_id=?",project,id);
        if(result.code().equals("TEXT_EXTRACTED_AI_PENDING")&&!result.pages().isEmpty()) {
            for(var page:result.pages()) db.update("INSERT INTO document_pages(id,project_id,document_id,page_number,content) VALUES (?,?,?,?,?)",
                UUID.randomUUID(),project,id,page.pageNumber(),page.text());
            db.update("UPDATE documents SET processing_status='UPLOADED',processing_code='TEXT_EXTRACTED_AI_PENDING',extracted_pages=?,updated_at=CURRENT_TIMESTAMP WHERE project_id=? AND id=?",
                result.pages().size(),project,id);
        } else {
            db.update("UPDATE documents SET processing_status='FAILED',processing_code=?,extracted_pages=0,updated_at=CURRENT_TIMESTAMP WHERE project_id=? AND id=?",result.code(),project,id);
        }
    }
    public void processing(UUID project,UUID id) {
        db.update("UPDATE documents SET processing_status='PROCESSING',processing_code='EXTRACTING_TEXT',updated_at=CURRENT_TIMESTAMP WHERE project_id=? AND id=?",project,id);
    }
    public void delete(UUID project,UUID id) {
        db.update("INSERT INTO pending_file_deletions(storage_key) SELECT storage_key FROM documents WHERE project_id=? AND id=?",project,id);
        db.update("DELETE FROM documents WHERE project_id=? AND id=?",project,id);
    }
}
