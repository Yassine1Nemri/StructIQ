package dev.structiq.document;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import dev.structiq.auth.AuthService;

@RestController
@RequestMapping("/api/projects/{projectId}/documents")
public class DocumentController {
    private final AuthService auth;
    private final DocumentService docs;
    public DocumentController(AuthService auth,DocumentService docs) { this.auth=auth; this.docs=docs; }
    @GetMapping public DocumentRepository.Page list(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID projectId,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return docs.list(auth.current(jwt),projectId,page,size);
    }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public DocumentRepository.Metadata upload(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID projectId,
                                               @RequestPart("file") MultipartFile file) {
        return docs.upload(auth.current(jwt),projectId,file);
    }
    @GetMapping("/{documentId}/download") public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID projectId,@PathVariable UUID documentId) {
        var value=docs.download(auth.current(jwt),projectId,documentId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(value.metadata().mimeType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(value.metadata().filename(),StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL,"no-store").header("X-Content-Type-Options","nosniff")
            .contentLength(value.bytes().length).body(value.bytes());
    }
    @PostMapping("/{documentId}/retry") public DocumentRepository.Metadata retry(@AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID projectId,@PathVariable UUID documentId) { return docs.retry(auth.current(jwt),projectId,documentId); }
    @DeleteMapping("/{documentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID projectId,@PathVariable UUID documentId) {
        docs.delete(auth.current(jwt),projectId,documentId);
    }
}
