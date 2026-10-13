package dev.seatsure.common;

import java.time.Instant;
import java.util.List;

/**
 * The single error shape returned by every endpoint. {@code details} is only populated where it
 * helps the UI, for example the list of seats that were taken by someone else.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<Long> details) {

    public static ApiError of(int status, String error, String message, String path, List<Long> details) {
        return new ApiError(Instant.now(), status, error, message, path, details);
    }
}
