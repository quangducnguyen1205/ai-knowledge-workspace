package com.aiknowledgeworkspace.workspacecore.processing.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.processing.application.port.in.ProcessingResultUseCase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Two deliveries of the same processing result, applied at the same instant, against real
 * PostgreSQL.
 *
 * <p>The inbox reads the event id, applies the business change, and writes the inbox row — a shape
 * that looks like a check-then-act race. It is not one, and this proves why rather than asserting
 * it: the read, the business change and the inbox insert all sit inside one transaction, and the
 * inbox event id is the table's primary key. The loser's whole transaction is rolled back with it,
 * so a duplicate delivery cannot apply the business change twice.
 *
 * <p>A single worker thread would prove nothing here, and the worker's concurrency is configuration
 * rather than a guarantee, so this runs the two deliveries on real threads against a real server
 * where the constraint is enforced. It is skipped unless PostgreSQL is offered through the
 * environment, and it creates and drops its own throwaway database:
 *
 * <pre>
 * WORKSPACE_CORE_IT_POSTGRES_URL=jdbc:postgresql://localhost:5434/postgres
 * WORKSPACE_CORE_IT_POSTGRES_USER=workspace_core
 * WORKSPACE_CORE_IT_POSTGRES_PASSWORD=workspace_core
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "WORKSPACE_CORE_IT_POSTGRES_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class ProcessingResultInboxConcurrencyPostgresTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-30T09:00:00Z");
    private static final long AWAIT_SECONDS = 30L;

    private static String adminUrl;
    private static String username;
    private static String password;
    private static String throwawayDatabase;

    @Autowired
    private ProcessingResultUseCase processingResults;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID assetId;
    private UUID requestEventId;

    @DynamicPropertySource
    static void throwawayPostgresDatabase(DynamicPropertyRegistry registry) throws Exception {
        adminUrl = System.getenv("WORKSPACE_CORE_IT_POSTGRES_URL");
        username = System.getenv("WORKSPACE_CORE_IT_POSTGRES_USER");
        password = System.getenv("WORKSPACE_CORE_IT_POSTGRES_PASSWORD");
        throwawayDatabase = "workspace_core_inbox_it_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + throwawayDatabase);
        }

        String testUrl = adminUrl.substring(0, adminUrl.lastIndexOf('/') + 1) + throwawayDatabase;
        registry.add("spring.datasource.url", () -> testUrl);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
    }

    @AfterAll
    static void dropThrowawayDatabase() throws Exception {
        if (throwawayDatabase == null) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '"
                            + throwawayDatabase + "' AND pid <> pg_backend_pid()"
            );
            statement.execute("DROP DATABASE IF EXISTS " + throwawayDatabase);
        }
    }

    @BeforeEach
    void seedAssetAwaitingItsResult() {
        UUID workspaceId = UUID.randomUUID();
        assetId = UUID.randomUUID();
        requestEventId = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO workspaces (id, name, owner_id, default_workspace, created_at)"
                        + " VALUES (?, ?, ?, ?, now())",
                workspaceId, "Inbox concurrency", "owner-1", false
        );
        jdbcTemplate.update(
                "INSERT INTO assets (id, original_filename, title, status, workspace_id,"
                        + " storage_bucket, object_key, content_type, size_bytes, etag,"
                        + " source_type, created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'PROCESSING', ?, ?, ?, ?, ?, ?, 'UPLOAD', now(), now())",
                assetId, "lecture.mp4", "Inbox concurrency lecture", workspaceId,
                "workspace-media", "objects/" + assetId + ".mp4", "video/mp4", 42L, "etag-1"
        );
        jdbcTemplate.update(
                "INSERT INTO processing_jobs (id, asset_id, processing_job_status,"
                        + " processing_request_event_id, created_at, updated_at)"
                        + " VALUES (?, ?, 'PENDING', ?, now(), now())",
                UUID.randomUUID(), assetId, requestEventId
        );
    }

    @Test
    void twoSimultaneousDeliveriesOfTheSameResultApplyItExactlyOnce() throws Exception {
        UUID resultEventId = UUID.randomUUID();
        String event = failedEvent(resultEventId);

        CyclicBarrier bothReady = new CyclicBarrier(2);
        AtomicInteger applied = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        Callable<Void> delivery = () -> {
            bothReady.await(AWAIT_SECONDS, TimeUnit.SECONDS);
            try {
                if (processingResults.handle(event).applied()) {
                    applied.incrementAndGet();
                }
            } catch (RuntimeException exception) {
                // The loser's transaction is rolled back by the primary key. On the Kafka path that
                // surfaces as an uncommitted offset and the delivery is retried.
                rejected.incrementAndGet();
            }
            return null;
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Void>> outcomes = executor.invokeAll(List.of(delivery, delivery));
            for (Future<Void> outcome : outcomes) {
                outcome.get(AWAIT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(applied.get())
                .withFailMessage(
                        "expected exactly one delivery to apply the business change; applied=%d rejected=%d",
                        applied.get(), rejected.get())
                .isEqualTo(1);
        assertThat(inboxRowCount(resultEventId)).isEqualTo(1);
        assertThat(inboxStatus(resultEventId)).isEqualTo("APPLIED");
        assertThat(jobStatus()).isEqualTo("FAILED");
        assertThat(assetStatus()).isEqualTo("FAILED");
    }

    @Test
    void aSecondDeliveryAfterTheFirstCompletedIsRecognisedAsADuplicate() {
        UUID resultEventId = UUID.randomUUID();
        String event = failedEvent(resultEventId);

        assertThat(processingResults.handle(event).applied()).isTrue();
        assertThat(processingResults.handle(event).applied()).isFalse();

        assertThat(inboxRowCount(resultEventId)).isEqualTo(1);
        assertThat(inboxStatus(resultEventId)).isEqualTo("APPLIED");
        assertThat(jobStatus()).isEqualTo("FAILED");
    }

    private String failedEvent(UUID resultEventId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "asset.processing.failed",
                  "eventVersion": 1,
                  "aggregateType": "ASSET",
                  "aggregateId": "%s",
                  "eventKey": "%s",
                  "causationEventId": "%s",
                  "occurredAt": "%s",
                  "payload": {
                    "processingRequestId": "%s",
                    "errorCode": "PROCESSING_FAILED",
                    "message": "processing failed"
                  }
                }
                """.formatted(
                resultEventId, assetId, assetId, requestEventId, OCCURRED_AT, requestEventId
        );
    }

    private int inboxRowCount(UUID resultEventId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM consumed_processing_result_events WHERE event_id = ?",
                Integer.class, resultEventId
        );
    }

    private String inboxStatus(UUID resultEventId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM consumed_processing_result_events WHERE event_id = ?",
                String.class, resultEventId
        );
    }

    private String jobStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT processing_job_status FROM processing_jobs WHERE asset_id = ?",
                String.class, assetId
        );
    }

    private String assetStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM assets WHERE id = ?", String.class, assetId
        );
    }
}
