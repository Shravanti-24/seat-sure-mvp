-- The two operator accounts. The BCrypt hashes are injected as Flyway placeholders that
-- SeedPasswordFlywayCustomizer computes from SEED_ADMIN_PASSWORD / SEED_STAFF_PASSWORD at startup,
-- so no password or hash is ever committed to the repository.

insert into users (name, email, password_hash, role) values
    ('SeatSure Admin', 'admin@seatsure.dev', '${adminPasswordHash}', 'ADMIN'),
    ('Gate Staff', 'gate@seatsure.dev', '${staffPasswordHash}', 'STAFF');
