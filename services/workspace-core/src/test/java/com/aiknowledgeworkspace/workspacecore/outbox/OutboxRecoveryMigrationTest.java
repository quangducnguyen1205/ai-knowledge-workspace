package com.aiknowledgeworkspace.workspacecore.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class OutboxRecoveryMigrationTest {

    @Test
    void historicalFailedRowsAreBackfilledAsUnknownAndRemainTerminal() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:workspace-core-outbox-recovery-migration;"
                        + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
        );
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("10"))
                .load()
                .migrate();

        UUID eventId = UUID.randomUUID();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update(
                """
                        insert into outbox_events (
                            id, event_type, event_version, aggregate_type, aggregate_id,
                            event_key, payload, status, attempt_count, created_at, updated_at
                        ) values (?, 'asset.processing.requested', 1, 'ASSET', ?, ?, '{}', 'FAILED', 5,
                                  current_timestamp, current_timestamp)
                        """,
                eventId,
                UUID.randomUUID(),
                eventId.toString()
        );

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                        select status, failure_disposition, recovery_cycle_count,
                               next_recovery_at, last_failure_category, last_error
                        from outbox_events where id = ?
                        """,
                eventId
        );
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("failure_disposition")).isEqualTo("UNKNOWN");
        assertThat(row.get("recovery_cycle_count")).isEqualTo(0);
        assertThat(row.get("next_recovery_at")).isNull();
        assertThat(row.get("last_failure_category")).isEqualTo("HISTORICAL_UNCLASSIFIED");
        assertThat(row.get("last_error")).isEqualTo("HISTORICAL_UNCLASSIFIED");
    }
}
