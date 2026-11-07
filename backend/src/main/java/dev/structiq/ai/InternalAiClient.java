package dev.structiq.ai;

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
import dev.structiq.common.ApiException;

@Component
public class InternalAiClient {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final JsonMapper json=new JsonMapper();
    private final String url,secret;
    public InternalAiClient(@Value("${structiq.ai-url}") String url,@Value("${structiq.internal-secret}") String secret) {
        this.url=url; this.secret=secret;
    }
    public <T> T post(String operation,Object payload,Class<T> type) {
        try {
            String path="/internal/"+operation,body=json.writeValueAsString(payload);
            String timestamp=Long.toString(Instant.now().getEpochSecond()),nonce=UUID.randomUUID().toString();
            String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
            var mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            String signature=HexFormat.of().formatHex(mac.doFinal(("POST\n"+path+"\n"+timestamp+"\n"+nonce+"\n"+digest).getBytes(StandardCharsets.UTF_8)));
            var request=HttpRequest.newBuilder(URI.create(url+path)).timeout(Duration.ofSeconds(150))
                .header("Content-Type","application/json").header("X-Internal-Timestamp",timestamp).header("X-Internal-Nonce",nonce)
                .header("X-Internal-Signature",signature).POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200) {
                String code=json.readTree(response.body()).path("code").asText();
                if(!Set.of("AI_NOT_CONFIGURED","AI_PROVIDER_UNAVAILABLE","AI_PROVIDER_TIMEOUT","AI_RATE_LIMITED","AI_BUSY","AI_INVALID_RESPONSE","AI_INVALID_EMBEDDING","NO_TEXT").contains(code)) code="AI_UNAVAILABLE";
                throw new ApiException(response.statusCode()==429?429:503,code,
                    code.equals("AI_NOT_CONFIGURED")?"AI is not configured. Configure Ollama or Gemini on the internal service.":"AI is unavailable. Check the configured model service and retry.");
            }
            return json.readValue(response.body(),type);
        } catch(ApiException ex) { throw ex; }
        catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new ApiException(503,"AI_UNAVAILABLE","AI is unavailable. Please retry later."); }
        catch(Exception ex) { throw new ApiException(503,"AI_UNAVAILABLE","AI is unavailable. Please retry later."); }
    }
}
