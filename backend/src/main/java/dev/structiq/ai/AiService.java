package dev.structiq.ai;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import dev.structiq.common.ApiException;
import dev.structiq.project.ProjectRepository;
import dev.structiq.project.ProjectService;
import dev.structiq.user.UserRepository.User;

@Service
public class AiService {
    public record Status(boolean configured) {}
    public record IndexResult(int indexedChunks) {}
    public record Citation(UUID chunkId,UUID documentId,String filename,Integer pageNumber,String excerpt) {}
    public record Answer(String answer,List<Citation> citations,int retrievalCount) {}
    private final ProjectService projects;
    private final ProjectRepository projectRows;
    private final InternalAiClient client;
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    public AiService(ProjectService projects,ProjectRepository projectRows,InternalAiClient client,JdbcTemplate db,TransactionTemplate tx) {
        this.projects=projects; this.projectRows=projectRows; this.client=client; this.db=db; this.tx=tx;
    }
    public Status status(User user,UUID project) {
        projects.get(user,project);
        return client.post("status",Map.of("projectId",project.toString()),Status.class);
    }
    public Answer ask(User user,UUID project,String question) {
        // Keep membership and source deletion stable while context is sent to the model.
        return tx.execute(transaction -> {
            projectRows.lock(project); projects.get(user,project);
            var answer=client.post("ask",Map.of("projectId",project.toString(),"question",question.strip()),Answer.class);
            if(answer==null||answer.answer()==null||answer.answer().isBlank()||answer.answer().length()>8000||answer.citations()==null
                ||answer.citations().size()>6||answer.retrievalCount()<0||answer.retrievalCount()>6) throw invalid();
            var seen=new HashSet<UUID>();
            for(var citation:answer.citations()) {
                if(citation==null||citation.chunkId()==null||citation.documentId()==null||citation.excerpt()==null||citation.excerpt().isBlank()
                    ||citation.excerpt().length()>400||citation.pageNumber()==null||!seen.add(citation.chunkId())) throw invalid();
                int matches=db.queryForObject("""
                    SELECT COUNT(*) FROM document_chunks c JOIN documents d ON d.id=c.document_id AND d.project_id=c.project_id
                    WHERE c.id=? AND c.document_id=? AND c.project_id=? AND d.project_id=? AND d.processing_status='READY'
                      AND d.original_filename=? AND c.page_number=? AND c.content LIKE ? ESCAPE '!'
                    """,Integer.class,citation.chunkId(),citation.documentId(),project,project,citation.filename(),citation.pageNumber(),
                    citation.excerpt().replace("!","!!").replace("%","!%").replace("_","!_")+"%");
                if(matches!=1) throw invalid();
            }
            if(answer.citations().size()>answer.retrievalCount()) throw invalid();
            if(answer.citations().isEmpty()&&!answer.answer().equals("The project documents do not contain enough relevant information to answer this question.")) throw invalid();
            return answer;
        });
    }
    public IndexResult index(User user,UUID project,UUID document) {
        return tx.execute(transaction -> {
            projectRows.lock(project);
            if(projects.get(user,project).role().equals("VIEWER")) throw ApiException.forbidden();
            var codes=db.queryForList("SELECT processing_code FROM documents WHERE project_id=? AND id=?",String.class,project,document);
            if(codes.isEmpty()) throw ApiException.missing();
            if(db.queryForObject("SELECT COUNT(*) FROM document_pages WHERE project_id=? AND document_id=?",Integer.class,project,document)==0)
                throw new ApiException(409,"NO_TEXT","Extract document text before indexing");
            var result=client.post("index",Map.of("projectId",project.toString(),"documentId",document.toString()),IndexResult.class);
            if(result==null||result.indexedChunks()<1||result.indexedChunks()>800) throw invalid();
            return result;
        });
    }
    private ApiException invalid() { return new ApiException(503,"AI_INVALID_RESPONSE","AI returned an invalid response. Please retry later."); }
}
