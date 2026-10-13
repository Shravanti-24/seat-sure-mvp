package dev.seatsure.common;

import java.util.List;
import org.springframework.http.HttpStatus;

/** Base class for the application's expected failures, each carrying the HTTP status to return. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final List<Long> details;

    public ApiException(HttpStatus status, String message) {
        this(status, message, List.of());
    }

    public ApiException(HttpStatus status, String message, List<Long> details) {
        super(message);
        this.status = status;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public List<Long> getDetails() {
        return details;
    }
}
