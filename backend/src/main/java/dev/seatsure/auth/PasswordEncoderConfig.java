package dev.seatsure.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Deliberately separate from {@link SecurityConfig}. The Flyway customizer that hashes the seeded
 * passwords needs the encoder before the security filter chain exists, and keeping the bean here
 * stops that from becoming a circular dependency.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
