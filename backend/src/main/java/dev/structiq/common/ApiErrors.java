package dev.structiq.common;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrors {
    public record ErrorBody(String code, String message, String correlationId) {}
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorBody> oversized(Exception ex,HttpServletRequest req) {
        return api(new ApiException(413,"FILE_TOO_LARGE","The file exceeds the configured upload limit"),req);
    }
    @ExceptionHandler({org.springframework.web.multipart.MultipartException.class,
        org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ResponseEntity<ErrorBody> multipart(Exception ex,HttpServletRequest req) {
        return api(new ApiException(400,"INVALID_UPLOAD","Supply one PDF or TXT file in the file field"),req);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<ErrorBody> notFound(Exception ex, HttpServletRequest req) {
        return api(ApiException.missing(), req);
    }

    public static void write(HttpServletRequest req, HttpServletResponse res, int status,
                             String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message
            + "\",\"correlationId\":\"" + req.getAttribute("correlationId") + "\"}");
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> api(ApiException ex, HttpServletRequest req) {
        var response = ResponseEntity.status(ex.status);
        if (ex.status == 429) response.header("Retry-After", "60");
        return response.body(new ErrorBody(ex.code, ex.getMessage(),
            String.valueOf(req.getAttribute("correlationId"))));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorBody> invalid(Exception ex, HttpServletRequest req) {
        return api(new ApiException(400, "INVALID_INPUT", "Check the supplied fields and date range"), req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorBody> conflict(Exception ex, HttpServletRequest req) {
        return api(new ApiException(409, "CONFLICT", "The operation conflicts with existing data"), req);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> unexpected(Exception ex, HttpServletRequest req) {
        // Never log request bodies, tokens, credentials, or database exception text.
        return api(new ApiException(500, "INTERNAL_ERROR", "An unexpected error occurred"), req);
    }
}
