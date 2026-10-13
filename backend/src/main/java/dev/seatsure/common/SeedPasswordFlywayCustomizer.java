package dev.seatsure.common;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The seeded accounts live in a Flyway migration, but their passwords come from the environment.
 * This customizer hashes those passwords with the application's own {@link PasswordEncoder} and
 * hands the hashes to Flyway as placeholders, so no credential is ever committed to the repository.
 */
@Component
public class SeedPasswordFlywayCustomizer implements FlywayConfigurationCustomizer {

    private final SeatSureProperties properties;
    private final PasswordEncoder passwordEncoder;

    public SeedPasswordFlywayCustomizer(SeatSureProperties properties, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void customize(FluentConfiguration configuration) {
        configuration.placeholders(java.util.Map.of(
                "adminPasswordHash", passwordEncoder.encode(properties.seed().adminPassword()),
                "staffPasswordHash", passwordEncoder.encode(properties.seed().staffPassword()),
                "loadtestPasswordHash", passwordEncoder.encode(properties.seed().loadtestPassword()),
                "highDemandSaleDelayMinutes", String.valueOf(properties.seed().highDemandSaleDelayMinutes())));
    }
}
