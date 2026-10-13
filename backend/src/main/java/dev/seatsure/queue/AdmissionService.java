package dev.seatsure.queue;

import dev.seatsure.common.ForbiddenException;
import dev.seatsure.common.SeatSureProperties;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Admission tokens. Being let out of the queue means holding
 * {@code admit:{eventId}:{userId}} in Redis; the hold endpoint refuses to work for a high-demand
 * event without the matching token, so nobody can skip the line by calling the API directly.
 */
@Service
public class AdmissionService {

    private final StringRedisTemplate redis;
    private final Duration admissionTtl;
    private final long tickIntervalMs;

    public AdmissionService(StringRedisTemplate redis, SeatSureProperties properties) {
        this.redis = redis;
        this.admissionTtl = Duration.ofSeconds(properties.queue().admissionTtlSeconds());
        this.tickIntervalMs = properties.queue().tickIntervalMs();
    }

    public long tickIntervalMs() {
        return tickIntervalMs;
    }

    /** @return the freshly issued token */
    public String admit(long eventId, long userId) {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(QueueKeys.admission(eventId, userId), token, admissionTtl);
        return token;
    }

    public String tokenFor(long eventId, long userId) {
        return redis.opsForValue().get(QueueKeys.admission(eventId, userId));
    }

    public Long admissionTtlSeconds(long eventId, long userId) {
        return redis.getExpire(QueueKeys.admission(eventId, userId));
    }

    public void requireAdmission(long eventId, long userId, String presentedToken) {
        String expected = tokenFor(eventId, userId);
        if (expected == null) {
            throw new ForbiddenException("Join the queue");
        }
        if (presentedToken == null
                || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8), presentedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ForbiddenException("Your admission token is missing or no longer valid");
        }
    }
}
