package dev.practicedebt.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for tests that need the real database.
 *
 * <p>Not a stylistic choice: the schema uses {@code text[]}, GIN indexes, {@code on conflict} and
 * {@code generated always as identity}, none of which an in-memory database will run. Testing the
 * debt derivation anywhere but PostgreSQL would be testing a different query.
 *
 * <p>The container is a singleton started once per JVM rather than per class, and Testcontainers
 * shuts it down with Ryuk at the end. Flyway runs against it, so a broken migration fails the
 * build here rather than on someone's next startup.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // These tests must never reach Codeforces. Mirrors are driven explicitly.
                "codeforces.mirror.refresh-on-startup=false"
        })
@Testcontainers
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18-alpine");

    static {
        POSTGRES.start();
    }
}
