package dev.seatsure.common;

import java.util.List;
import org.springframework.http.HttpStatus;

/** 409: somebody else got there first, or the client asked for something that no longer fits. */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }

    public ConflictException(String message, List<Long> unavailableSeatIds) {
        super(HttpStatus.CONFLICT, message, unavailableSeatIds);
    }
}
