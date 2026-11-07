package dev.structiq;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FoundationTest {
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    @Test void livenessIsPublicAndDoesNotExposeDetails() throws Exception {
        var response = get("/actuator/health/liveness");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test void statusReportsFoundationWithoutClaimingAi() throws Exception {
        var response = get("/api/system/status");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("StructIQ", "SECURE_RAG", "OPTIONAL_GEMINI");
    }

    @Test void projectRoutesFailClosed() throws Exception {
        var response = get("/api/projects");
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("UNAUTHENTICATED");
        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
    }

    @Test void sensitiveActuatorEndpointsAreNotPublic() throws Exception {
        assertThat(get("/actuator/env").statusCode()).isEqualTo(401);
    }

    @Test void documentationIsDisabledByDefault() throws Exception {
        assertThat(get("/v3/api-docs").statusCode()).isEqualTo(401);
        assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(401);
    }

    @Test void untrustedCorsOriginIsRejected() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/system/status"))
            .header("Origin", "https://untrusted.example").build();
        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }
}
