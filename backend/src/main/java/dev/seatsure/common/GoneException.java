package dev.seatsure.common;

import org.springframework.http.HttpStatus;

/** 410: the seat hold expired while the user was on the checkout page. */
public class GoneException extends ApiException {

    public GoneException(String message) {
        super(HttpStatus.GONE, message);
    }
}
