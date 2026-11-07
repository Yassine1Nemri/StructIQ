package dev.structiq.document;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import dev.structiq.common.ApiException;
import dev.structiq.project.ProjectRepository;
import dev.structiq.project.ProjectService;
import dev.structiq.user.UserRepository.User;
import static dev.structiq.document.DocumentRepository.*;

@Service
public class DocumentService {
    public record Download(Metadata metadata,byte[] bytes) {}
    private final DocumentRepository docs;
    private final PrivateStorage storage;
    private final UploadValidator validator;
    private final ExtractionClient parser;
    private final DocumentCleanup cleanup;
    private final ProjectService projects;
    private final ProjectRepository projectRows;
    private final TransactionTemplate tx;
    public DocumentService(DocumentRepository docs,PrivateStorage storage,UploadValidator validator,ExtractionClient parser,
        DocumentCleanup cleanup,ProjectService projects,ProjectRepository projectRows,TransactionTemplate tx) {
        this.docs=docs; this.storage=storage; this.validator=validator; this.parser=parser; this.cleanup=cleanup;
        this.projects=projects; this.projectRows=projectRows; this.tx=tx;
    }
    public Page list(User user,UUID project,int page,int size) {
        return tx.execute(status -> {
            projects.get(user,project);
            if(page<0||page>100_000||size<1||size>100) throw new ApiException(400,"INVALID_INPUT","Invalid pagination");
            var rows=docs.list(project,size+1,page*size);
            return new Page(rows.stream().limit(size).toList(),page,size,rows.size()>size,validator.maxBytes());
        });
    }
    private void uploadAccess(User user,UUID project) {
        if(projects.get(user,project).role().equals("VIEWER")) throw ApiException.forbidden();
    }
    public Metadata upload(User user,UUID project,MultipartFile file) {
        UUID id=UUID.randomUUID();
        var stored=tx.execute(status -> {
            projectRows.lock(project); uploadAccess(user,project);
            var upload=validator.validate(file);
            String key=storage.key(project,id);
            try { storage.write(key,upload.bytes()); }
            catch(IOException ex) { throw new ApiException(503,"STORAGE_UNAVAILABLE","Private storage is unavailable"); }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int outcome) {
                    if(outcome!=STATUS_COMMITTED) try { storage.delete(key); } catch(IOException ignored) { /* See crash recovery limits in docs. */ }
                }
            });
            try {
                String checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(upload.bytes()));
                docs.insert(id,project,user.id(),upload,key,checksum);
            } catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
            return docs.find(project,id).orElseThrow(ApiException::missing);
        });
        return process(user,project,stored);
    }
    public Metadata retry(User user,UUID project,UUID id) {
        var stored=tx.execute(status -> {
            projectRows.lock(project); uploadAccess(user,project);
            var found=docs.find(project,id).orElseThrow(ApiException::missing);
            if(!found.metadata().processingStatus().equals("FAILED")) throw new ApiException(409,"NOT_RETRYABLE","Only failed extraction can be retried");
            docs.processing(project,id);
            // State is committed before the synchronous parser call so lists reflect real work.
            return found;
        });
        return process(user,project,stored);
    }
    private Metadata process(User user,UUID project,Stored stored) {
        var result=parser.extract(project,stored.metadata().id(),stored.metadata().mimeType());
        tx.executeWithoutResult(status -> {
            projectRows.lock(project);
            if(docs.find(project,stored.metadata().id()).isPresent()) docs.finish(project,stored.metadata().id(),result);
        });
        return tx.execute(status -> {
            projects.get(user,project);
            return docs.find(project,stored.metadata().id()).orElseThrow(ApiException::missing).metadata();
        });
    }
    public Download download(User user,UUID project,UUID id) {
        return tx.execute(status -> {
            projectRows.lock(project); projects.get(user,project);
            var stored=docs.find(project,id).orElseThrow(ApiException::missing);
            try { return new Download(stored.metadata(),storage.read(stored.storageKey())); }
            catch(IOException ex) { throw new ApiException(503,"STORAGE_UNAVAILABLE","The private file is unavailable"); }
        });
    }
    public void delete(User user,UUID project,UUID id) {
        tx.executeWithoutResult(status -> {
            projectRows.lock(project);
            String role=projects.get(user,project).role();
            var stored=docs.find(project,id).orElseThrow(ApiException::missing);
            if(role.equals("VIEWER")||(!role.equals("ADMIN")&&!role.equals("MANAGER")&&!stored.metadata().uploadedBy().equals(user.id())))
                throw ApiException.forbidden();
            docs.delete(project,id); cleanup.afterCommit();
        });
    }
}
