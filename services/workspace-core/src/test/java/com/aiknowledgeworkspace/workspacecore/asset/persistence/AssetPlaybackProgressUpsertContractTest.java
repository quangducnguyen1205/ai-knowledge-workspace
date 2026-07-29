package com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Guards the concurrency boundary in every build, including the H2 profile that cannot execute the
 * statement. If someone replaces the atomic upsert with a read-then-branch algorithm, this fails
 * even though the PostgreSQL integration test is not selected.
 */
class AssetPlaybackProgressUpsertContractTest {

    private static final String UPSERT_SQL =
            AssetPlaybackProgressJpaRepository.UPSERT_SQL.toLowerCase(Locale.ROOT);

    @Test
    void theWriteIsOneAtomicInsertOnConflictStatementKeyedByAssetAndUser() {
        assertThat(UPSERT_SQL)
                .contains("insert into asset_playback_progress")
                .contains("on conflict (asset_id, user_id)")
                .contains("do update set")
                .contains("position_ms = excluded.position_ms")
                .contains("completed = excluded.completed")
                .contains("updated_at = excluded.updated_at");
    }

    @Test
    void theStatementDoesNotSelectOrLockBeforeWriting() {
        assertThat(UPSERT_SQL)
                .doesNotContain("select")
                .doesNotContain("for update")
                .doesNotContain("lock");
    }

    @Test
    void theUpsertIsExposedAsOneNativeModifyingRepositoryOperation() throws Exception {
        Method upsert = AssetPlaybackProgressJpaRepository.class.getDeclaredMethod(
                "upsert", UUID.class, String.class, long.class, boolean.class, Instant.class
        );
        Query query = upsert.getAnnotation(Query.class);
        Modifying modifying = upsert.getAnnotation(Modifying.class);

        assertThat(query).isNotNull();
        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value()).isEqualTo(AssetPlaybackProgressJpaRepository.UPSERT_SQL);
        assertThat(modifying).isNotNull();
        assertThat(modifying.flushAutomatically()).isTrue();
        assertThat(modifying.clearAutomatically()).isTrue();
    }

    @Test
    void theRepositoryExposesNoReadThenBranchWriteHelper() {
        assertThat(AssetPlaybackProgressJpaRepository.class.getDeclaredMethods())
                .extracting(Method::getName)
                .containsExactlyInAnyOrder("upsert", "deleteByAssetId");
    }
}
