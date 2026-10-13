package dev.seatsure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.seatsure.common.SeatSureProperties;
import dev.seatsure.ratelimit.RateLimiter.Decision;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    @DisplayName("requests inside the limit are allowed and the key is given a TTL on first use")
    void allowsWithinLimit() {
        RateLimiter limiter = limiter(Map.of("holds", 10), Instant.parse("2026-01-01T10:00:30Z"));
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("rl:7:holds:29454360")).thenReturn(1L);

        Decision decision = limiter.check(7L, "holds");

        assertThat(decision.allowed()).isTrue();
        verify(redis).expire("rl:7:holds:29454360", Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("the TTL is only set once per window, not on every request")
    void setsExpiryOnlyOnFirstRequest() {
        RateLimiter limiter = limiter(Map.of("holds", 10), Instant.parse("2026-01-01T10:00:30Z"));
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(4L);

        assertThat(limiter.check(7L, "holds").allowed()).isTrue();
        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("the request after the limit is denied with the seconds left in the window")
    void deniesOverLimit() {
        RateLimiter limiter = limiter(Map.of("holds", 10), Instant.parse("2026-01-01T10:00:30Z"));
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(11L);

        Decision decision = limiter.check(7L, "holds");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(30);
    }

    @Test
    @DisplayName("each user has their own budget")
    void countsPerUser() {
        RateLimiter limiter = limiter(Map.of("holds", 10), Instant.parse("2026-01-01T10:00:00Z"));
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("rl:1:holds:29454360")).thenReturn(11L);
        when(valueOperations.increment("rl:2:holds:29454360")).thenReturn(1L);

        assertThat(limiter.check(1L, "holds").allowed()).isFalse();
        assertThat(limiter.check(2L, "holds").allowed()).isTrue();
    }

    @Test
    @DisplayName("an endpoint group with no configured limit is not limited")
    void allowsUnconfiguredGroup() {
        RateLimiter limiter = limiter(Map.of("holds", 10), Instant.parse("2026-01-01T10:00:00Z"));

        assertThat(limiter.check(7L, "reports").allowed()).isTrue();
        verify(redis, never()).opsForValue();
    }

    @Test
    @DisplayName("limiting can be switched off entirely")
    void respectsDisabledFlag() {
        SeatSureProperties properties = properties(Map.of("holds", 1), false);
        RateLimiter limiter = new RateLimiter(
                redis, properties, Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC));

        assertThat(limiter.check(7L, "holds").allowed()).isTrue();
        verify(redis, never()).opsForValue();
    }

    private RateLimiter limiter(Map<String, Integer> limits, Instant now) {
        return new RateLimiter(redis, properties(limits, true), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static SeatSureProperties properties(Map<String, Integer> limits, boolean enabled) {
        return new SeatSureProperties(
                new SeatSureProperties.Jwt("jwt-secret-that-is-long-enough-for-hs256", 720),
                new SeatSureProperties.Hold(420, 6, 30, 30000),
                new SeatSureProperties.Ticket("hmac"),
                new SeatSureProperties.Queue(25, 5000, 600, 60),
                new SeatSureProperties.RateLimit(enabled, 60, limits),
                new SeatSureProperties.Seed("a", "b", "c", 2));
    }
}
