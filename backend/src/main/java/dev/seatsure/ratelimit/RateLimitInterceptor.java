package dev.seatsure.ratelimit;

import dev.seatsure.auth.AppUserPrincipal;
import dev.seatsure.common.RateLimitExceededException;
import dev.seatsure.ratelimit.RateLimiter.Decision;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Applies {@link RateLimited} before the controller method runs. */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;

    public RateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimited rateLimited = handlerMethod.getMethodAnnotation(RateLimited.class);
        if (rateLimited == null) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            // Unauthenticated requests are rejected by Spring Security anyway.
            return true;
        }
        Decision decision = rateLimiter.check(principal.getId(), rateLimited.group());
        if (!decision.allowed()) {
            throw new RateLimitExceededException(
                    "You are going too fast, try again in " + decision.retryAfterSeconds() + "s",
                    decision.retryAfterSeconds());
        }
        return true;
    }
}
