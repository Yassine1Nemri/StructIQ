package dev.structiq.document;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ExtractionClient {
    public record Page(int pageNumber,String text) {}
    public record Result(List<Page> pages,String code) {}
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final JsonMapper json=new JsonMapper();
    private final String url,secret;
    public ExtractionClient(@Value("${structiq.ai-url}") String url,@Value("${structiq.internal-secret}") String secret) {
        if(secret.getBytes(StandardCharsets.UTF_8).length<32||secret.startsWith("replace-")) throw new IllegalArgumentException("Internal secret must be independently generated and at least 32 bytes");
        this.url=url; this.secret=secret;
    }
    public Result extract(UUID project,UUID document,String mimeType) {
        try {
            String body=json.writeValueAsString(Map.of("projectId",project.toString(),"documentId",document.toString(),"mimeType",mimeType));
            String timestamp=Long.toString(Instant.now().getEpochSecond()),nonce=UUID.randomUUID().toString();
            String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
            String canonical="POST\n/internal/extract\n"+timestamp+"\n"+nonce+"\n"+digest;
            var mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            String signature=HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
            var request=HttpRequest.newBuilder(URI.create(url+"/internal/extract")).timeout(Duration.ofSeconds(25))
                .header("Content-Type","application/json").header("X-Internal-Timestamp",timestamp)
                .header("X-Internal-Nonce",nonce).header("X-Internal-Signature",signature)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200) return new Result(List.of(),"PARSER_UNAVAILABLE");
            var value=json.readValue(response.body(),Result.class);
            var allowed=Set.of("TEXT_EXTRACTED_AI_PENDING","INVALID_TEXT","INVALID_PDF","ENCRYPTED_PDF","NO_TEXT",
                "EXTRACTION_LIMIT","EXTRACTION_TIMEOUT","EXTRACTION_FAILED","INVALID_STORAGE","INVALID_TYPE");
            if(value==null||!allowed.contains(value.code())||value.pages()==null||value.pages().size()>200) throw new IllegalArgumentException();
            int chars=0; var seen=new HashSet<Integer>();
            for(var page:value.pages()) {
                if(page.pageNumber()<1||page.pageNumber()>200||!seen.add(page.pageNumber())||page.text()==null||page.text().isBlank()) throw new IllegalArgumentException();
                chars+=page.text().length();
            }
            if(chars>500_000) throw new IllegalArgumentException();
            return value;
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt(); return new Result(List.of(),"PARSER_UNAVAILABLE");
        } catch(Exception ex) { return new Result(List.of(),"PARSER_UNAVAILABLE"); }
    }
}
