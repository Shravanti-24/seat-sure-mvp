package dev.seatsure.auth;

import dev.seatsure.common.SeatSureProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Issues and verifies the stateless HS256 access tokens. */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration expiry;
    private final Clock clock;

    public JwtService(SeatSureProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.expiry = Duration.ofMinutes(properties.jwt().expiryMinutes());
        this.clock = clock;
    }

    public String issue(User user) {
        Date issuedAt = Date.from(clock.instant());
        Date expiresAt = Date.from(clock.instant().plus(expiry));
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(key)
                .compact();
    }

    public long expirySeconds() {
        return expiry.toSeconds();
    }

    /** @return the email in the token subject, or null when the token is missing or invalid. */
    public String parseSubject(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}
