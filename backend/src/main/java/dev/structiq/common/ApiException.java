package dev.structiq.common;

public class ApiException extends RuntimeException {
    public final int status;
    public final String code;
    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public static ApiException missing() {
        return new ApiException(404, "NOT_FOUND", "Resource not found");
    }
    public static ApiException forbidden() {
        return new ApiException(403, "FORBIDDEN", "This operation requires a project manager");
    }
}
