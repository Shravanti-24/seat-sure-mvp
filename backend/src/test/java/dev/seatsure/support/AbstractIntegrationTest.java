package dev.seatsure.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Postgres and real Redis for every integration test: the behaviour under test (TTLs, Lua
 * scripts, partial unique indexes, optimistic locking) simply does not exist in an in-memory
 * substitute.
 *
 * <p>By default Testcontainers starts both, which is what CI does. If
 * {@code SEATSURE_TEST_DATASOURCE_URL} and {@code SEATSURE_TEST_REDIS_URL} are set, those servers
 * are used instead; that is the escape hatch for a machine where Docker is unavailable or too new
 * for the Testcontainers client. In that mode the public schema is dropped first so Flyway replays
 * from scratch and the run is repeatable.
 *
 * <p>The containers are static, so all integration tests share one pair rather than paying the
 * startup cost per class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    private static final String EXTERNAL_DATASOURCE_URL = System.getenv("SEATSURE_TEST_DATASOURCE_URL");
    private static final String EXTERNAL_DATASOURCE_USER =
            System.getenv().getOrDefault("SEATSURE_TEST_DATASOURCE_USERNAME", "seatsure");
    private static final String EXTERNAL_DATASOURCE_PASSWORD =
            System.getenv().getOrDefault("SEATSURE_TEST_DATASOURCE_PASSWORD", "seatsure");
    private static final String EXTERNAL_REDIS_URL = System.getenv("SEATSURE_TEST_REDIS_URL");

    private static final boolean USE_CONTAINERS = EXTERNAL_DATASOURCE_URL == null || EXTERNAL_REDIS_URL == null;

    private static PostgreSQLContainer<?> postgres;
    private static GenericContainer<?> redis;

    static {
        if (USE_CONTAINERS) {
            postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("seatsure")
                    .withUsername("seatsure")
                    .withPassword("seatsure");
            redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
            postgres.start();
            redis.start();
        } else {
            resetExternalSchema();
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        if (USE_CONTAINERS) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.data.redis.url",
                    () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        } else {
            registry.add("spring.datasource.url", () -> EXTERNAL_DATASOURCE_URL);
            registry.add("spring.datasource.username", () -> EXTERNAL_DATASOURCE_USER);
            registry.add("spring.datasource.password", () -> EXTERNAL_DATASOURCE_PASSWORD);
            registry.add("spring.data.redis.url", () -> EXTERNAL_REDIS_URL);
        }
        // Rate limiting has its own tests; leaving it on would throttle the concurrency tests.
        registry.add("seatsure.rate-limit.enabled", () -> "false");
        // The seeded high-demand event must already be on sale when the queue tests run.
        registry.add("seatsure.seed.high-demand-sale-delay-minutes", () -> "0");
        // Admit in small, frequent batches so the waiting room test does not wait five seconds.
        registry.add("seatsure.queue.tick-interval-ms", () -> "500");
        registry.add("seatsure.queue.batch-size", () -> "25");
    }

    private static void resetExternalSchema() {
        try (Connection connection = DriverManager.getConnection(
                        EXTERNAL_DATASOURCE_URL, EXTERNAL_DATASOURCE_USER, EXTERNAL_DATASOURCE_PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.execute("drop schema public cascade");
            statement.execute("create schema public");
        } catch (Exception ex) {
            throw new IllegalStateException("Could not reset the external test database", ex);
        }
    }

    @Autowired
    protected TestRestTemplate rest;

    @LocalServerPort
    protected int port;
}
