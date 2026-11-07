package dev.structiq.document;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class DocumentCleanup {
    @Configuration @EnableScheduling static class Scheduling {}
    private final JdbcTemplate db;
    private final PrivateStorage storage;
    private final boolean enabled;
    private final TransactionTemplate tx;
    public DocumentCleanup(JdbcTemplate db,PrivateStorage storage,PlatformTransactionManager manager,@Value("${structiq.cleanup-enabled:true}") boolean enabled) {
        this.db=db; this.storage=storage; this.enabled=enabled;
        this.tx=new TransactionTemplate(manager); this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public void afterCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { try { drain(); } catch(RuntimeException ignored) { /* Durable rows are retried. */ } }
        });
    }
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void retry() {
        if(enabled) {
            db.update("UPDATE documents SET processing_status='FAILED',processing_code='PROCESSING_INTERRUPTED',updated_at=CURRENT_TIMESTAMP WHERE processing_status='PROCESSING' AND updated_at<?",
                java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(2));
            drain();
        }
    }
    public synchronized void drain() {
        tx.executeWithoutResult(status -> drainRows());
    }
    private void drainRows() {
        for(String key:db.queryForList("SELECT storage_key FROM pending_file_deletions ORDER BY created_at LIMIT 100",String.class)) {
            try {
                storage.delete(key);
                db.update("DELETE FROM pending_file_deletions WHERE storage_key=?",key);
            } catch(IOException ex) {
                // Keep the durable deletion request. Never log private filenames or storage paths.
            }
        }
    }
}
