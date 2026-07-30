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

        assertThat(flyway(dataSource, "4").migrate().migrationsExecuted).isEqualTo(1);

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

        assertThat(flyway(dataSource, "4").migrate().migrationsExecuted).isEqualTo(1);

        assertInvalidYoutubeVideoId(jdbc, workspaceId, "video id");
        assertInvalidYoutubeVideoId(jdbc, workspaceId, " video-id ");
        assertInvalidYoutubeVideoId(jdbc, workspaceId, "video\u0001id");
        assertInvalidYoutubeVideoId(jdbc, workspaceId, "   ");
        assertInvalidYoutubeVideoId(jdbc, workspaceId, "a".repeat(129));

        insertYoutubeAsset(jdbc, UUID.randomUUID(), workspaceId, "Safe_ID-123");
    }

    @Test
    void v5AddsUserScopedPlaybackProgressWithoutTouchingExistingProductTables() {
        JdbcDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(flyway(dataSource, "4").migrate().migrationsExecuted).isEqualTo(4);

        UUID workspaceId = insertWorkspace(jdbc, "Playback progress");
        UUID uploadAssetId = UUID.randomUUID();
        insertSourceAwareAsset(
                jdbc, uploadAssetId, workspaceId, "UPLOAD", null,
                "fixture.mp4", "workspace-media", "objects/fixture.mp4", "video/mp4", 42L, "legacy-etag"
        );
        Map<String, Object> uploadBeforeV5 = loadUploadFields(jdbc, uploadAssetId);
        assertThat(columns(jdbc, "assets")).doesNotContain("position_ms", "completed");

        assertThat(flyway(dataSource, "5").migrate().migrationsExecuted).isEqualTo(1);

        assertThat(columns(jdbc, "asset_playback_progress"))
                .containsExactlyInAnyOrder("asset_id", "user_id", "position_ms", "completed", "updated_at");
        assertThat(constraints(jdbc, "asset_playback_progress")).contains(
                "pk_asset_playback_progress",
                "ck_asset_playback_progress_position_non_negative",
                "fk_asset_playback_progress_asset"
        );
        assertThat(loadUploadFields(jdbc, uploadAssetId)).isEqualTo(uploadBeforeV5);

        UUID youtubeAssetId = UUID.randomUUID();
        insertYoutubeAsset(jdbc, youtubeAssetId, workspaceId, "Safe_ID-123");

        insertPlaybackProgress(jdbc, uploadAssetId, "user-1", 12345L, false);
        insertPlaybackProgress(jdbc, uploadAssetId, "user-2", 0L, true);
        insertPlaybackProgress(jdbc, youtubeAssetId, "user-1", 6789L, false);

        assertInvalidAsset(() -> insertPlaybackProgress(jdbc, uploadAssetId, "user-1", 1L, false));
        assertInvalidAsset(() -> insertPlaybackProgress(jdbc, uploadAssetId, "user-3", -1L, false));
        assertInvalidAsset(() -> insertPlaybackProgress(jdbc, UUID.randomUUID(), "user-1", 1L, false));

        assertThat(progressRowCount(jdbc, uploadAssetId)).isEqualTo(2);
        jdbc.update("delete from assets where id = ?", uploadAssetId);
        assertThat(progressRowCount(jdbc, uploadAssetId)).isZero();
        assertThat(progressRowCount(jdbc, youtubeAssetId)).isEqualTo(1);
    }

    @Test
    void v6AddsSavedMomentsWithoutTouchingExistingProductTables() {
        JdbcDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(flyway(dataSource, "5").migrate().migrationsExecuted).isEqualTo(5);

        UUID workspaceId = insertWorkspace(jdbc, "Saved moments");
        UUID uploadAssetId = UUID.randomUUID();
        insertSourceAwareAsset(
                jdbc, uploadAssetId, workspaceId, "UPLOAD", null,
                "fixture.mp4", "workspace-media", "objects/fixture.mp4", "video/mp4", 42L, "legacy-etag"
        );
        UUID youtubeAssetId = UUID.randomUUID();
        insertYoutubeAsset(jdbc, youtubeAssetId, workspaceId, "Safe_ID-456");
        insertPlaybackProgress(jdbc, uploadAssetId, "user-1", 12345L, false);
        Map<String, Object> uploadBeforeV6 = loadUploadFields(jdbc, uploadAssetId);

        assertThat(flyway(dataSource, "6").migrate().migrationsExecuted).isEqualTo(1);

        assertThat(columns(jdbc, "saved_moments")).containsExactlyInAnyOrder(
                "id", "user_id", "workspace_id", "asset_id", "transcript_row_id", "saved_at"
        );
        assertThat(constraints(jdbc, "saved_moments")).contains(
                "pk_saved_moments",
                "uk_saved_moments_user_asset_row",
                "ck_saved_moments_transcript_row_id_not_blank",
                "fk_saved_moments_asset"
        );
        assertThat(loadUploadFields(jdbc, uploadAssetId)).isEqualTo(uploadBeforeV6);
        assertThat(progressRowCount(jdbc, uploadAssetId)).isEqualTo(1);
        assertThat(columns(jdbc, "assets")).doesNotContain("transcript_row_id", "saved_at");

        insertSavedMoment(jdbc, uploadAssetId, workspaceId, "user-1", "row-a");
        insertSavedMoment(jdbc, uploadAssetId, workspaceId, "user-1", "row-b");
        insertSavedMoment(jdbc, uploadAssetId, workspaceId, "user-2", "row-a");
        insertSavedMoment(jdbc, youtubeAssetId, workspaceId, "user-1", "row-a");

        assertInvalidAsset(() -> insertSavedMoment(jdbc, uploadAssetId, workspaceId, "user-1", "row-a"));
        assertInvalidAsset(() -> insertSavedMoment(jdbc, uploadAssetId, workspaceId, "user-1", "   "));
        assertInvalidAsset(() -> insertSavedMoment(jdbc, UUID.randomUUID(), workspaceId, "user-1", "row-a"));

        assertThat(savedMomentRowCount(jdbc, uploadAssetId)).isEqualTo(3);
        jdbc.update("delete from assets where id = ?", uploadAssetId);
        assertThat(savedMomentRowCount(jdbc, uploadAssetId)).isZero();
        assertThat(savedMomentRowCount(jdbc, youtubeAssetId)).isEqualTo(1);
    }

    @Test
    void v7AddsTheResumablePlaybackIndexWithoutChangingAnyTableOrConstraint() {
        JdbcDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(flyway(dataSource, "6").migrate().migrationsExecuted).isEqualTo(6);

        UUID workspaceId = insertWorkspace(jdbc, "Resumable index");
        UUID assetId = UUID.randomUUID();
        insertSourceAwareAsset(
                jdbc, assetId, workspaceId, "UPLOAD", null,
                "fixture.mp4", "workspace-media", "objects/fixture.mp4", "video/mp4", 42L, "legacy-etag"
        );
        insertPlaybackProgress(jdbc, assetId, "user-1", 12345L, false);
        insertPlaybackProgress(jdbc, assetId, "user-2", 0L, false);
        insertPlaybackProgress(jdbc, assetId, "user-3", 999L, true);
        Map<String, Object> uploadBeforeV7 = loadUploadFields(jdbc, assetId);
        List<String> progressColumnsBeforeV7 = columns(jdbc, "asset_playback_progress");
        List<String> progressConstraintsBeforeV7 = constraints(jdbc, "asset_playback_progress");

        assertThat(flyway(dataSource, null).migrate().migrationsExecuted).isEqualTo(1);

        assertThat(columns(jdbc, "asset_playback_progress")).isEqualTo(progressColumnsBeforeV7);
        assertThat(constraints(jdbc, "asset_playback_progress")).isEqualTo(progressConstraintsBeforeV7);
        assertThat(loadUploadFields(jdbc, assetId)).isEqualTo(uploadBeforeV7);
        assertThat(progressRowCount(jdbc, assetId)).isEqualTo(3);

        // Existing writes stay valid: the index is additive and carries no uniqueness.
        UUID secondAssetId = UUID.randomUUID();
        insertSourceAwareAsset(
                jdbc, secondAssetId, workspaceId, "UPLOAD", null,
                "second.mp4", "workspace-media", "objects/second.mp4", "video/mp4", 42L, null
        );
        insertPlaybackProgress(jdbc, secondAssetId, "user-1", 777L, false);
        jdbc.update("update asset_playback_progress set position_ms = 0 where asset_id = ? and user_id = ?",
                secondAssetId, "user-1");
        jdbc.update("delete from asset_playback_progress where asset_id = ? and user_id = ?",
                secondAssetId, "user-1");
        assertThat(progressRowCount(jdbc, secondAssetId)).isZero();
        assertThat(progressRowCount(jdbc, assetId)).isEqualTo(3);
    }

    private void insertSavedMoment(
            JdbcTemplate jdbc,
            UUID assetId,
            UUID workspaceId,
            String userId,
            String transcriptRowId
    ) {
        jdbc.update(
                "insert into saved_moments (id, user_id, workspace_id, asset_id, transcript_row_id, saved_at) "
                        + "values (?, ?, ?, ?, ?, current_timestamp)",
                UUID.randomUUID(), userId, workspaceId, assetId, transcriptRowId
        );
    }

    private int savedMomentRowCount(JdbcTemplate jdbc, UUID assetId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from saved_moments where asset_id = ?", Integer.class, assetId
        );
        return count == null ? 0 : count;
    }

    private void insertPlaybackProgress(
            JdbcTemplate jdbc,
            UUID assetId,
            String userId,
            long positionMs,
            boolean completed
    ) {
        jdbc.update(
                "insert into asset_playback_progress (asset_id, user_id, position_ms, completed, updated_at) "
                        + "values (?, ?, ?, ?, current_timestamp)",
                assetId, userId, positionMs, completed
        );
    }

    private int progressRowCount(JdbcTemplate jdbc, UUID assetId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from asset_playback_progress where asset_id = ?",
                Integer.class,
                assetId
        );
        return count == null ? 0 : count;
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
