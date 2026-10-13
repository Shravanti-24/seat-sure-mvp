package dev.seatsure.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a handler as rate limited. Endpoints that share a {@code group} share one budget, so a
 * script cannot spread its load across several URLs to get around the limit.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    String group();
}
