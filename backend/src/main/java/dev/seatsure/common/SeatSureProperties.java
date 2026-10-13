package dev.seatsure.common;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All tunables live here so that behaviour under load (hold duration, admission batch size,
 * rate limits) can be changed through environment variables without a rebuild.
 */
@ConfigurationProperties(prefix = "seatsure")
public record SeatSureProperties(
        Jwt jwt,
        Hold hold,
        Ticket ticket,
        Queue queue,
        RateLimit rateLimit,
        Seed seed) {

    public record Jwt(String secret, long expiryMinutes) {}

    public record Hold(long durationSeconds, int maxSeatsPerRequest, long expiryJobGraceSeconds,
            long expiryJobIntervalMs) {}

    public record Ticket(String hmacSecret) {}

    public record Queue(int batchSize, long tickIntervalMs, long admissionTtlSeconds,
            long abandonAfterSeconds) {}

    public record RateLimit(boolean enabled, long windowSeconds, Map<String, Integer> limits) {}

    public record Seed(String adminPassword, String staffPassword, String loadtestPassword,
            int highDemandSaleDelayMinutes) {}
}
