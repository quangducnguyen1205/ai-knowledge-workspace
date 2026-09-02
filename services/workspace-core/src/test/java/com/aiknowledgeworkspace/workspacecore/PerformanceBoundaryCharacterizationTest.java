package com.aiknowledgeworkspace.workspacecore;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowInput;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.CanonicalTranscriptStore;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;
import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Measures a boundary an audit flagged, so it is classified from numbers rather than from reading
 * the code. Nothing here asserts a latency budget — wall time on in-memory H2 is not a production
 * number. What it does assert is the structural cost that does not depend on the engine: how many
 * JDBC statements one transcript write costs. That count is what scales with the input.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-perf-boundary;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class PerformanceBoundaryCharacterizationTest {

    /** TranscriptArtifactValidator.MAX_TRANSCRIPT_ROWS — the largest artifact the product accepts. */
    private static final int ENFORCED_TRANSCRIPT_CEILING = 20_000;
    /** Whisper segments for the longest supported media (YOUTUBE_MAX_DURATION_SECONDS = 7200s). */
    private static final int REALISTIC_TRANSCRIPT_ROWS = 2_000;
    private static final String SEGMENT_TEXT =
            "The recursion terminates once the subproblem size reaches the base case, "
                    + "which is why the overall complexity stays logarithmic in the input.";

    @Autowired
    private CanonicalTranscriptStore transcriptStore;

    @Autowired
    private AssetStore assetStore;

    @Autowired
    private WorkspaceStore workspaceStore;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    void aRealisticTranscriptWriteCostsFarFewerRoundTripsThanRows() {
        UUID assetId = uploadAsset(workspace("owner-perf-1"), "Realistic lecture");

        long statements = measureTranscriptWrite(assetId, REALISTIC_TRANSCRIPT_ROWS);

        System.out.printf(
                "PERF transcript rows=%d jdbc_statements=%d%n", REALISTIC_TRANSCRIPT_ROWS, statements
        );
        assertBatched(statements, REALISTIC_TRANSCRIPT_ROWS);
    }

    @Test
    void theLargestAcceptedTranscriptStillWritesInBatches() {
        UUID assetId = uploadAsset(workspace("owner-perf-2"), "Ceiling lecture");

        long start = System.nanoTime();
        long statements = measureTranscriptWrite(assetId, ENFORCED_TRANSCRIPT_CEILING);
        long millis = (System.nanoTime() - start) / 1_000_000;

        System.out.printf(
                "PERF transcript rows=%d jdbc_statements=%d h2_millis=%d%n",
                ENFORCED_TRANSCRIPT_CEILING, statements, millis
        );
        assertBatched(statements, ENFORCED_TRANSCRIPT_CEILING);
    }

    /**
     * One statement per row is the unbatched shape. The exact batch size is configuration, so this
     * asserts the order of magnitude rather than a count: a write must not scale one round trip per
     * transcript row.
     */
    private void assertBatched(long statements, int rowCount) {
        assertThat(statements)
                .withFailMessage(
                        "%d rows cost %d JDBC statements; batching is not in effect",
                        rowCount, statements)
                .isLessThan(rowCount / 4L);
    }

    private long measureTranscriptWrite(UUID assetId, int rowCount) {
        List<AssetTranscriptRowInput> rows = transcriptRows(rowCount);
        Statistics statistics = statistics();
        statistics.clear();
        transactionTemplate.executeWithoutResult(status -> transcriptStore.replace(assetId, rows));
        return statistics.getPrepareStatementCount();
    }

    private List<AssetTranscriptRowInput> transcriptRows(int count) {
        List<AssetTranscriptRowInput> rows = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            rows.add(new AssetTranscriptRowInput(
                    "row-" + index,
                    "video-1",
                    index,
                    index * 5_000L,
                    (index + 1) * 5_000L,
                    SEGMENT_TEXT,
                    "2026-07-01T10:15:30Z"
            ));
        }
        return rows;
    }

    private UUID workspace(String ownerId) {
        return workspaceStore.save(
                new Workspace(UUID.randomUUID(), "Perf boundary", ownerId, false)
        ).getId();
    }

    private UUID uploadAsset(UUID workspaceId, String title) {
        UUID assetId = UUID.randomUUID();
        return assetStore.save(Asset.uploaded(
                assetId, "lecture.mp4", title, AssetStatus.SEARCHABLE, workspaceId,
                "workspace-media", "objects/" + assetId + ".mp4", "video/mp4", 42L, "etag-1"
        )).getId();
    }
}
