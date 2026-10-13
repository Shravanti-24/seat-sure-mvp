package dev.seatsure.booking;

import dev.seatsure.common.SeatSureProperties;
import dev.seatsure.event.HeldSeatLookup;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * The only place that knows about hold keys in Redis. A hold is simply
 * {@code hold:{eventId}:{seatId} = userId} with a TTL, which is why an abandoned checkout cleans
 * itself up with no job and no column to reset.
 */
@Component
public class HoldRegistry implements HeldSeatLookup {

    private final StringRedisTemplate redis;
    private final RedisScript<List> acquireScript;
    private final RedisScript<Long> releaseScript;
    private final Duration holdDuration;

    @SuppressWarnings("rawtypes")
    public HoldRegistry(
            StringRedisTemplate redis,
            RedisScript<List> acquireHoldsScript,
            RedisScript<Long> releaseHoldsScript,
            SeatSureProperties properties) {
        this.redis = redis;
        this.acquireScript = acquireHoldsScript;
        this.releaseScript = releaseHoldsScript;
        this.holdDuration = Duration.ofSeconds(properties.hold().durationSeconds());
    }

    public Duration holdDuration() {
        return holdDuration;
    }

    static String key(long eventId, long seatId) {
        return "hold:" + eventId + ":" + seatId;
    }

    private static long seatIdFromKey(String key) {
        return Long.parseLong(key.substring(key.lastIndexOf(':') + 1));
    }

    /**
     * Claims every seat or none.
     *
     * @return the seat ids that somebody else already holds; empty means this user now holds them all
     */
    @SuppressWarnings("unchecked")
    public List<Long> acquire(long eventId, long userId, List<Long> seatIds) {
        List<String> keys = seatIds.stream().map(seatId -> key(eventId, seatId)).toList();
        List<String> failedKeys = redis.execute(
                acquireScript, keys, String.valueOf(userId), String.valueOf(holdDuration.toSeconds()));
        if (failedKeys == null || failedKeys.isEmpty()) {
            return List.of();
        }
        return failedKeys.stream().map(HoldRegistry::seatIdFromKey).toList();
    }

    /** Releases the user's own holds. Safe to call twice. */
    public long release(long eventId, long userId, Collection<Long> seatIds) {
        if (seatIds.isEmpty()) {
            return 0;
        }
        List<String> keys = seatIds.stream().map(seatId -> key(eventId, seatId)).toList();
        Long released = redis.execute(releaseScript, keys, String.valueOf(userId));
        return released == null ? 0 : released;
    }

    /** True only if every seat is still held by this user. Used right before taking the money. */
    public boolean allHeldBy(long eventId, long userId, Collection<Long> seatIds) {
        if (seatIds.isEmpty()) {
            return false;
        }
        List<String> keys = seatIds.stream().map(seatId -> key(eventId, seatId)).toList();
        List<String> owners = redis.opsForValue().multiGet(keys);
        if (owners == null || owners.size() != keys.size()) {
            return false;
        }
        String expected = String.valueOf(userId);
        return owners.stream().allMatch(expected::equals);
    }

    @Override
    public Set<Long> heldSeatIds(long eventId, Collection<Long> seatIds) {
        if (seatIds.isEmpty()) {
            return Set.of();
        }
        List<Long> ordered = List.copyOf(seatIds);
        List<String> keys = ordered.stream().map(seatId -> key(eventId, seatId)).toList();
        List<String> owners = redis.opsForValue().multiGet(keys);
        Set<Long> held = new HashSet<>();
        if (owners == null) {
            return held;
        }
        for (int i = 0; i < ordered.size() && i < owners.size(); i++) {
            if (owners.get(i) != null) {
                held.add(ordered.get(i));
            }
        }
        return held;
    }
}
