package com.aiknowledgeworkspace.workspacecore.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class CleanBaselineMigrationTest {

    @Test
    void immutableV1ThenV2ThenV3ThenV4PreserveUploadsAndEnforceSourceShapes() {
        JdbcDataSource dataSource = dataSource();

        assertThat(flyway(dataSource, "1").migrate().migrationsExecuted).isEqualTo(1);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(columns(jdbc, "asset_transcript_rows"))
                .doesNotContain("start_ms", "end_ms");
        assertThat(columns(jdbc, "assets"))
                .doesNotContain("source_type", "youtube_video_id");

        assertThat(flyway(dataSource, "2").migrate().migrationsExecuted).isEqualTo(1);
        assertPhaseOneSchema(jdbc);

        UUID legacyWorkspaceId = insertWorkspace(jdbc, "Legacy uploads");
        UUID legacyAssetId = UUID.randomUUID();
        insertLegacyUpload(jdbc, legacyAssetId, legacyWorkspaceId);
        Map<String, Object> legacyBeforeV3 = loadUploadFields(jdbc, legacyAssetId);

        assertThat(flyway(dataSource, "3").migrate().migrationsExecuted).isEqualTo(1);

        assertThat(columns(jdbc, "assets")).contains("source_type", "youtube_video_id");
        assertThat(nullable(jdbc, "source_type")).isEqualTo("NO");
        assertThat(nullable(jdbc, "youtube_video_id")).isEqualTo("YES");
        assertThat(defaultValue(jdbc, "source_type")).isNull();
        assertThat(constraints(jdbc, "assets")).contains(
                "ck_assets_source_type",
                "ck_assets_youtube_video_id_valid",
                "ck_assets_source_shape",
                "uk_assets_workspace_youtube_video"
        );

        assertThat(loadUploadFields(jdbc, legacyAssetId)).isEqualTo(legacyBeforeV3);
        assertThat(jdbc.queryForObject(
                "select source_type from assets where id = ?",
                String.class,
                legacyAssetId
        )).isEqualTo("UPLOAD");
        assertThat(jdbc.queryForObject(
                "select youtube_video_id from assets where id = ?",
                String.class,
                legacyAssetId
        )).isNull();

        assertThat(flyway(dataSource, null).migrate().migrationsExecuted).isEqualTo(1);

        UUID workspaceOne = insertWorkspace(jdbc, "Workspace one");
        UUID workspaceTwo = insertWorkspace(jdbc, "Workspace two");

        insertSourceAwareAsset(
                jdbc, UUID.randomUUID(), workspaceOne, "UPLOAD", null,
                "first.mp4", "workspace-media", "objects/first.mp4", "video/mp4", 1L, "etag-1"
        );
        insertSourceAwareAsset(
                jdbc, UUID.randomUUID(), workspaceOne, "UPLOAD", null,
                "second.mp4", "workspace-media", "objects/second.mp4", "video/mp4", 0L, null
        );
        insertSourceAwareAsset(
                jdbc, UUID.randomUUID(), workspaceOne, "YOUTUBE", "same-video",
                null, null, null, null, null, null
        );
        insertSourceAwareAsset(
                jdbc, UUID.randomUUID(), workspaceTwo, "YOUTUBE", "same-video",
                null, null, null, null, null, null
        );

        assertInvalidAsset(() -> insertSourceAwareAsset(
                jdbc, UUID.randomUUID(), workspaceOne, "UPLOAD", "mixed-video",
                "mixed.mp4", "workspace-media", "objects/mixed.mp4", "video/mp4", 1L, null
        ));
        assertInvalidAsset(() -> insertSourceAwareAsset(
                jdbc, UUID.randomUUID(), workspaceOne, "YOUTUBE", "mixed-storage",
                null, "workspace-media", "objects/mixed", null, null, null
        ));
        assertInvalidAsset(() -> insertSourceAwareAsset(
                jdbc, UUID.randomUUID(), workspaceOne, "YOUTUBE", "   ",
                null, null, null, null, null, null
        ));
        assertInvalidAsset(() -> insertSourceAwareAsset(
                jdbc, UUID.randomUUID(), workspaceOne, "UPLOAD", null,
                "negative.mp4", "workspace-media", "objects/negative.mp4", "video/mp4", -1L, null
        ));
        assertInvalidAsset(() -> insertSourceAwareAsset(
                jdbc, UUID.randomUUID(), workspaceOne, "YOUTUBE", "same-video",
                null, null, null, null, null, null
        ));

        jdbc.update(
                "insert into asset_transcript_rows (snapshot_id, asset_id, transcript_row_id, video_id, "
                        + "segment_index, text, created_at) values (?, ?, 'legacy', 'video-1', 0, 'legacy', 'now')",
                UUID.randomUUID(), legacyAssetId
        );
        jdbc.update(
                "insert into asset_transcript_rows (snapshot_id, asset_id, transcript_row_id, video_id, "
                        + "segment_index, start_ms, end_ms, text, created_at) "
                        + "values (?, ?, 'timed', 'video-1', 1, 0, 1000, 'timed', 'now')",
                UUID.randomUUID(), legacyAssetId
        );
        assertInvalidTiming(jdbc, legacyAssetId, "partial", 2, 0L, null);
        assertInvalidTiming(jdbc, legacyAssetId, "negative", 3, -1L, 0L);
        assertInvalidTiming(jdbc, legacyAssetId, "backward", 4, 100L, 99L);
    }

    @Test
    void v4RejectsEveryYoutubeVideoIdOutsideTheDomainSafeCharacterSet() {
        JdbcDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(flyway(dataSource, "3").migrate().migrationsExecuted).isEqualTo(3);

        UUID workspaceId = insertWorkspace(jdbc, "Constraint closure");
        UUID preV4UnsafeAssetId = UUID.randomUUID();
        insertYoutubeAsset(jdbc, preV4UnsafeAssetId, workspaceId, "video id");
        assertThat(jdbc.queryForObject(
                "select youtube_video_id from assets where id = ?",
                String.class,
                preV4UnsafeAssetId
        )).isEqualTo("video id");
        jdbc.update("delete from assets where id = ?", preV4UnsafeAssetId);

        assertThat(flyway(dataSource, null).migrate().migrationsExecuted).isEqualTo(1);

        assertInvalidYoutubeVideoId(jdbc, workspaceId, "video id");
        assertInvalidYoutubeVideoId(jdbc, workspaceId, " video-id ");
        assertInvalidYoutubeVideoId(jdbc, workspaceId, "video\u0001id");
        assertInvalidYoutubeVideoId(jdbc, workspaceId, "   ");
        assertInvalidYoutubeVideoId(jdbc, workspaceId, "a".repeat(129));

        insertYoutubeAsset(jdbc, UUID.randomUUID(), workspaceId, "Safe_ID-123");
    }

    private JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:workspace-core-clean-baseline-" + UUID.randomUUID() + ";"
                        + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
        );
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private Flyway flyway(JdbcDataSource dataSource, String target) {
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(MigrationVersion.fromVersion(target));
        }
        return configuration.load();
    }

    private void assertPhaseOneSchema(JdbcTemplate jdbc) {
        assertThat(columns(jdbc, "processing_jobs"))
                .contains("asset_id", "processing_job_status", "processing_request_event_id")
                .doesNotContain("fastapi_task_id", "fastapi_video_id");
        assertThat(columns(jdbc, "outbox_events")).contains(
                "failure_disposition",
                "recovery_cycle_count",
                "next_recovery_at",
                "last_failure_category",
                "recovery_exhausted_at"
        );
        assertThat(columns(jdbc, "asset_transcript_rows")).contains("start_ms", "end_ms");
        assertThat(constraints(jdbc, "asset_transcript_rows"))
                .contains("ck_asset_transcript_rows_timing");
    }

    private UUID insertWorkspace(JdbcTemplate jdbc, String name) {
        UUID workspaceId = UUID.randomUUID();
        jdbc.update(
                "insert into workspaces (id, name, owner_id, default_workspace, created_at) "
                        + "values (?, ?, 'owner-1', false, current_timestamp)",
                workspaceId,
                name
        );
        return workspaceId;
    }

    private void insertLegacyUpload(JdbcTemplate jdbc, UUID assetId, UUID workspaceId) {
        jdbc.update(
                "insert into assets (id, original_filename, title, status, workspace_id, storage_bucket, "
                        + "object_key, content_type, size_bytes, etag, created_at, updated_at) "
                        + "values (?, 'fixture.mp4', 'Fixture', 'TRANSCRIPT_READY', ?, 'workspace-media', "
                        + "'objects/fixture.mp4', 'video/mp4', 42, 'legacy-etag', current_timestamp, "
                        + "current_timestamp)",
                assetId,
                workspaceId
        );
    }

    private void insertSourceAwareAsset(
            JdbcTemplate jdbc,
            UUID assetId,
            UUID workspaceId,
            String sourceType,
            String youtubeVideoId,
            String originalFilename,
            String storageBucket,
            String objectKey,
            String contentType,
            Long sizeBytes,
            String eTag
    ) {
        jdbc.update(
                "insert into assets (id, source_type, youtube_video_id, original_filename, title, status, "
                        + "workspace_id, storage_bucket, object_key, content_type, size_bytes, etag, "
                        + "created_at, updated_at) values (?, ?, ?, ?, 'Fixture', 'PROCESSING', ?, ?, ?, ?, ?, ?, "
                        + "current_timestamp, current_timestamp)",
                assetId,
                sourceType,
                youtubeVideoId,
                originalFilename,
                workspaceId,
                storageBucket,
                objectKey,
                contentType,
                sizeBytes,
                eTag
        );
    }

    private void insertYoutubeAsset(
            JdbcTemplate jdbc,
            UUID assetId,
            UUID workspaceId,
            String youtubeVideoId
    ) {
        insertSourceAwareAsset(
                jdbc,
                assetId,
                workspaceId,
                "YOUTUBE",
                youtubeVideoId,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private Map<String, Object> loadUploadFields(JdbcTemplate jdbc, UUID assetId) {
        return jdbc.queryForMap(
                "select original_filename, title, status, workspace_id, storage_bucket, object_key, "
                        + "content_type, size_bytes, etag from assets where id = ?",
                assetId
        );
    }

    private List<String> columns(JdbcTemplate jdbc, String tableName) {
        return jdbc.queryForList(
                "select column_name from information_schema.columns where table_name = ?",
                String.class,
                tableName
        );
    }

    private String nullable(JdbcTemplate jdbc, String columnName) {
        return jdbc.queryForObject(
                "select is_nullable from information_schema.columns "
                        + "where table_name = 'assets' and column_name = ?",
                String.class,
                columnName
        );
    }

    private String defaultValue(JdbcTemplate jdbc, String columnName) {
        return jdbc.queryForObject(
                "select column_default from information_schema.columns "
                        + "where table_name = 'assets' and column_name = ?",
                String.class,
                columnName
        );
    }

    private List<String> constraints(JdbcTemplate jdbc, String tableName) {
        return jdbc.queryForList(
                "select constraint_name from information_schema.table_constraints where table_name = ?",
                String.class,
                tableName
        );
    }

    private void assertInvalidAsset(Runnable insert) {
        assertThatThrownBy(insert::run)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void assertInvalidYoutubeVideoId(
            JdbcTemplate jdbc,
            UUID workspaceId,
            String youtubeVideoId
    ) {
        assertInvalidAsset(() -> insertYoutubeAsset(
                jdbc,
                UUID.randomUUID(),
                workspaceId,
                youtubeVideoId
        ));
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
