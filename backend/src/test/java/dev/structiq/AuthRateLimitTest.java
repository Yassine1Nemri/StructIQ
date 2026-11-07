package dev.structiq;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:rate_limit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class AuthRateLimitTest {
    @LocalServerPort int port;
    @Test void loginEndpointRateLimitsWithGenericCredentialErrors() throws Exception {
        var client=HttpClient.newHttpClient();
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/auth/login"))
            .header("Content-Type","application/json").header("X-Forwarded-For","spoofed-address")
            .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"unknown@test.local\",\"password\":\"synthetic-invalid\"}")).build();
        HttpResponse<String> response = null;
        // Allow one fixed-window boundary without depending on wall-clock timing.
        for(int i=0;i<21;i++) {
            response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) break;
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.body()).contains("INVALID_CREDENTIALS").doesNotContain("unknown@test.local");
        }
        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(response.body()).contains("RATE_LIMITED","correlationId");
        assertThat(response.headers().firstValue("Retry-After")).contains("60");
    }
}
