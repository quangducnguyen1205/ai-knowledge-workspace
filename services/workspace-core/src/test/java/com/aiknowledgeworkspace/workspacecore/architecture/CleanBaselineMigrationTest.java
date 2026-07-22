package com.aiknowledgeworkspace.workspacecore.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class CleanBaselineMigrationTest {

    @Test
    void immutableV1ThenV2CreateTimestampAwareSchemaWithoutDirectUploadColumns() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:workspace-core-clean-baseline;"
                        + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
        );
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Flyway v1Flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("1"))
                .load();

        assertThat(v1Flyway.migrate().migrationsExecuted).isEqualTo(1);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(columns(jdbc, "asset_transcript_rows"))
                .doesNotContain("start_ms", "end_ms");

        Flyway v2Flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        assertThat(v2Flyway.migrate().migrationsExecuted).isEqualTo(1);

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

        assertThat(columns(jdbc, "asset_transcript_rows"))
                .contains("start_ms", "end_ms");
        assertThat(jdbc.queryForList(
                "select constraint_name from information_schema.table_constraints "
                        + "where table_name = 'asset_transcript_rows'",
                String.class
        )).contains("ck_asset_transcript_rows_timing");

        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        jdbc.update(
                "insert into workspaces (id, name, owner_id, default_workspace, created_at) "
                        + "values (?, 'Phase 1', 'owner-1', false, current_timestamp)",
                workspaceId
        );
        jdbc.update(
                "insert into assets (id, original_filename, title, status, workspace_id, storage_bucket, "
                        + "object_key, content_type, size_bytes, created_at, updated_at) "
                        + "values (?, 'fixture.mp4', 'Fixture', 'TRANSCRIPT_READY', ?, 'workspace-media', "
                        + "'objects/fixture.mp4', 'video/mp4', 1, current_timestamp, current_timestamp)",
                assetId, workspaceId
        );
        jdbc.update(
                "insert into asset_transcript_rows (snapshot_id, asset_id, transcript_row_id, video_id, "
                        + "segment_index, text, created_at) values (?, ?, 'legacy', 'video-1', 0, 'legacy', 'now')",
                UUID.randomUUID(), assetId
        );
        jdbc.update(
                "insert into asset_transcript_rows (snapshot_id, asset_id, transcript_row_id, video_id, "
                        + "segment_index, start_ms, end_ms, text, created_at) "
                        + "values (?, ?, 'timed', 'video-1', 1, 0, 1000, 'timed', 'now')",
                UUID.randomUUID(), assetId
        );
        assertInvalidTiming(jdbc, assetId, "partial", 2, 0L, null);
        assertInvalidTiming(jdbc, assetId, "negative", 3, -1L, 0L);
        assertInvalidTiming(jdbc, assetId, "backward", 4, 100L, 99L);
    }

    private List<String> columns(JdbcTemplate jdbc, String tableName) {
        return jdbc.queryForList(
                "select column_name from information_schema.columns where table_name = ?",
                String.class,
                tableName
        );
    }

    private void assertInvalidTiming(
            JdbcTemplate jdbc,
            UUID assetId,
            String rowId,
            int segmentIndex,
            Long startMs,
            Long endMs
    ) {
        assertThatThrownBy(() -> jdbc.update(
                "insert into asset_transcript_rows (snapshot_id, asset_id, transcript_row_id, video_id, "
                        + "segment_index, start_ms, end_ms, text, created_at) "
                        + "values (?, ?, ?, 'video-1', ?, ?, ?, 'invalid', 'now')",
                UUID.randomUUID(), assetId, rowId, segmentIndex, startMs, endMs
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
