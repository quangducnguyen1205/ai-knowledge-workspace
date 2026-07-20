package com.aiknowledgeworkspace.workspacecore.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CleanBaselineMigrationTest {

    @Test
    void oneBaselineMigrationCreatesFinalSchemaWithoutDirectUploadColumns() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:workspace-core-clean-baseline;"
                        + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
        );
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<String> processingColumns = jdbc.queryForList(
                "select column_name from information_schema.columns where table_name = 'processing_jobs'",
                String.class
        );
        assertThat(processingColumns)
                .contains("asset_id", "processing_job_status", "processing_request_event_id")
                .doesNotContain("fastapi_task_id", "fastapi_video_id");

        List<String> outboxColumns = jdbc.queryForList(
                "select column_name from information_schema.columns where table_name = 'outbox_events'",
                String.class
        );
        assertThat(outboxColumns).contains(
                "failure_disposition",
                "recovery_cycle_count",
                "next_recovery_at",
                "last_failure_category",
                "recovery_exhausted_at"
        );
    }
}
