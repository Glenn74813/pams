package com.avtech.pams.common;

import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** An error that maps straight to an HTTP status and a JSON body. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, Object> body;

    public ApiException(HttpStatus status, Map<String, Object> body) {
        super(String.valueOf(body.getOrDefault("error", body.get("message"))));
        this.status = status;
        this.body = body;
    }

    /** {"error": message} with the given status. */
    public static ApiException of(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return new ApiException(status, body);
    }

    public static ApiException notFound(String what) {
        return of(HttpStatus.NOT_FOUND, what + " not found.");
    }

    public static ApiException forbidden(String message) {
        return of(HttpStatus.FORBIDDEN, message);
    }

    /** 422 in the Laravel validation shape: {"message": ..., "errors": {field: [messages]}}. */
    public static ApiException validation(String field, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Validation failed");
        body.put("errors", Map.of(field, List.of(message)));
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, body);
    }

    public ApiException with(String key, Object value) {
        body.put(key, value);
        return this;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, Object> getBody() {
        return body;
    }
}
