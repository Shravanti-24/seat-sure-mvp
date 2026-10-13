package dev.seatsure.ratelimit;

import dev.seatsure.common.SeatSureProperties;
import java.time.Clock;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Per-user fixed window counter in Redis: {@code INCR rl:{userId}:{group}:{window}} with an EXPIRE
 * on first use. One round trip per request and nothing to clean up, at the cost of allowing a burst
 * across a window boundary. A sliding window or token bucket would smooth that out; for protecting
 * a booking API from scripts the fixed window is enough and far easier to reason about.
 */
@Component
public class RateLimiter {

    private final StringRedisTemplate redis;
    private final SeatSureProperties properties;
    private final Clock clock;

    public RateLimiter(StringRedisTemplate redis, SeatSureProperties properties, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    public Decision check(long userId, String group) {
        if (!properties.rateLimit().enabled()) {
            return Decision.allow();
        }
        Integer limit = properties.rateLimit().limits().get(group);
        if (limit == null) {
            return Decision.allow();
        }

        long windowSeconds = Math.max(1, properties.rateLimit().windowSeconds());
        long epochSecond = clock.instant().getEpochSecond();
        long window = epochSecond / windowSeconds;
        String key = "rl:" + userId + ":" + group + ":" + window;

        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(windowSeconds));
        }
        if (count != null && count > limit) {
            long retryAfter = windowSeconds - (epochSecond % windowSeconds);
            return Decision.deny(Math.max(1, retryAfter));
        }
        return Decision.allow();
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {

        static Decision allow() {
            return new Decision(true, 0);
        }

        static Decision deny(long retryAfterSeconds) {
            return new Decision(false, retryAfterSeconds);
        }
    }
}
