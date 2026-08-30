package com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowInput;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.CanonicalTranscriptContextTarget;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.CanonicalTranscriptStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetTranscriptQueryService;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;
import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class CanonicalTranscriptContextPostgresIT {

    private static final String DATABASE = "workspace_core_context";
    private static final String USERNAME = "workspace_core";
    private static final String PASSWORD = "workspace_core";
    private static final GenericContainer<?> POSTGRES =
            new GenericContainer<>(DockerImageName.parse("postgres:16.10-alpine"))
                    .withEnv("POSTGRES_DB", DATABASE)
                    .withEnv("POSTGRES_USER", USERNAME)
                    .withEnv("POSTGRES_PASSWORD", PASSWORD)
                    .withExposedPorts(5432);

    static {
        POSTGRES.start();
    }

    @Autowired
    private WorkspaceStore workspaceStore;

    @Autowired
    private AssetStore assetStore;

    @Autowired
    private CanonicalTranscriptStore transcriptStore;

    @Autowired
    private AssetTranscriptQueryService transcriptQueryService;

    @SpyBean
    private CanonicalTranscriptContextJdbcRepository contextRepository;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://"
                + POSTGRES.getHost()
                + ":"
                + POSTGRES.getMappedPort(5432)
                + "/"
                + DATABASE);
        registry.add("spring.datasource.username", () -> USERNAME);
        registry.add("spring.datasource.password", () -> PASSWORD);
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @BeforeEach
    void resetQuerySpy() {
        clearInvocations(contextRepository);
    }

    @BeforeEach
    void establishCurrentUserSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("CURRENT_USER_ID", "local-dev-user");
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request), true);
    }

    @AfterEach
    void clearCurrentUserSession() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void middleFirstLastGapsAndBlankRowsUseCanonicalUsableOrder() {
        UUID assetId = persistAsset(List.of(
                row("first", 0, 0L, 999L, "First row."),
                row("blank", 5, null, null, " \t\n "),
                row("middle", 10, 1000L, 1999L, "Middle row."),
                row("last", 20, 2000L, 2999L, "Last row.")
        ));

        var middle = transcriptStore.loadContextWindows(
                assetId,
                List.of(new CanonicalTranscriptContextTarget("middle", 10))
        ).getFirst();
        var first = transcriptStore.loadContextWindows(
                assetId,
                List.of(new CanonicalTranscriptContextTarget("first", 0))
        ).getFirst();
        var last = transcriptStore.loadContextWindows(
                assetId,
                List.of(new CanonicalTranscriptContextTarget("last", 20))
        ).getFirst();

        assertThat(middle.orderedRows()).extracting(row -> row.transcriptRowId())
                .containsExactly("first", "middle", "last");
        assertThat(first.orderedRows()).extracting(row -> row.transcriptRowId())
                .containsExactly("first", "middle");
        assertThat(last.orderedRows()).extracting(row -> row.transcriptRowId())
                .containsExactly("middle", "last");
        assertThat(middle.matchedRow()).satisfies(row -> {
            assertThat(row.segmentIndex()).isEqualTo(10);
            assertThat(row.startMs()).isEqualTo(1000L);
            assertThat(row.endMs()).isEqualTo(1999L);
            assertThat(row.text()).isEqualTo("Middle row.");
            assertThat(row.createdAt()).isEqualTo("2026-07-30T00:00:10Z");
        });
    }

    @Test
    void rowIdIsAuthoritativeAndSegmentFallbackRequiresCanonicalRowIdToBeAbsent() {
        UUID assetId = persistAsset(List.of(
                row("stable-row", 7, 7000L, 7999L, "Stable identity."),
                row(null, 8, null, null, "Legacy identity.")
        ));

        assertThat(transcriptStore.loadContextWindows(
                assetId,
                List.of(new CanonicalTranscriptContextTarget("stable-row", 7))
        )).singleElement().satisfies(window ->
                assertThat(window.matchedRow().transcriptRowId()).isEqualTo("stable-row")
        );
        assertThat(transcriptStore.loadContextWindows(
                assetId,
                List.of(new CanonicalTranscriptContextTarget("replaced-row", 7))
        )).isEmpty();
        assertThat(transcriptStore.loadContextWindows(
                assetId,
                List.of(new CanonicalTranscriptContextTarget(null, 7))
        )).singleElement().satisfies(window ->
                assertThat(window.matchedRow().transcriptRowId()).isEqualTo("stable-row")
        );
        assertThat(transcriptStore.loadContextWindows(
                assetId,
                List.of(new CanonicalTranscriptContextTarget(null, 8))
        )).singleElement().satisfies(window -> {
            assertThat(window.matchedRow().transcriptRowId()).isNull();
            assertThat(window.matchedRow().startMs()).isNull();
            assertThat(window.matchedRow().endMs()).isNull();
        });
    }

    @Test
    void multipleTargetsForOneAssetUseOneStatementAndRetainIndependentWindows() {
        UUID assetId = persistAsset(List.of(
                row("zero", 0, 0L, 999L, "Zero."),
                row("one", 1, 1000L, 1999L, "One."),
                row("two", 2, 2000L, 2999L, "Two."),
                row("three", 3, 3000L, 3999L, "Three."),
                row("four", 4, 4000L, 4999L, "Four.")
        ));
        List<CanonicalTranscriptContextTarget> targets = List.of(
                new CanonicalTranscriptContextTarget("one", 1),
                new CanonicalTranscriptContextTarget("three", 3)
        );
        clearInvocations(contextRepository);

        var windows = transcriptStore.loadContextWindows(assetId, targets);

        assertThat(windows).hasSize(2);
        assertThat(window(windows, "one").orderedRows()).extracting(row -> row.text())
                .containsExactly("Zero.", "One.", "Two.");
        assertThat(window(windows, "three").orderedRows()).extracting(row -> row.text())
                .containsExactly("Two.", "Three.", "Four.");
        verify(contextRepository).load(assetId, targets);
    }

    @Test
    void differentAssetsCannotSupplyNeighborsOrMatchedRows() {
        UUID firstAssetId = persistAsset(List.of(
                row("first-hit", 1, 1000L, 1999L, "First Asset.")
        ));
        UUID secondAssetId = persistAsset(List.of(
                row("second-hit", 0, 0L, 999L, "Second Asset.")
        ));

        assertThat(transcriptStore.loadContextWindows(
                firstAssetId,
                List.of(
                        new CanonicalTranscriptContextTarget("first-hit", 1),
                        new CanonicalTranscriptContextTarget("second-hit", 0)
                )
        )).singleElement().satisfies(window -> {
            assertThat(window.matchedRow().transcriptRowId()).isEqualTo("first-hit");
            assertThat(window.orderedRows()).extracting(row -> row.transcriptRowId())
                    .doesNotContain("second-hit");
        });
        assertThat(transcriptStore.loadContextWindows(
                secondAssetId,
                List.of(new CanonicalTranscriptContextTarget("second-hit", 0))
        )).hasSize(1);
    }

    @Test
    void applicationBoundaryEnforcesWorkspaceOwnershipAndSearchableStatusOnPostgres() {
        AssetFixture searchable = persistAssetFixture(
                List.of(row("searchable-row", 1, 1000L, 1999L, "Searchable.")),
                AssetStatus.SEARCHABLE,
                "local-dev-user"
        );
        AssetFixture notSearchable = persistAssetFixture(
                List.of(row("ready-row", 1, 1000L, 1999L, "Transcript ready.")),
                AssetStatus.TRANSCRIPT_READY,
                "local-dev-user"
        );
        AssetFixture notOwned = persistAssetFixture(
                List.of(row("private-row", 1, 1000L, 1999L, "Private.")),
                AssetStatus.SEARCHABLE,
                "other-owner"
        );
        CanonicalTranscriptContextTarget target =
                new CanonicalTranscriptContextTarget("searchable-row", 1);

        assertThat(transcriptQueryService.findSearchableTranscriptContexts(
                searchable.assetId(), searchable.workspaceId(), List.of(target)
        )).hasSize(1);
        assertThat(transcriptQueryService.findSearchableTranscriptContexts(
                searchable.assetId(), UUID.randomUUID(), List.of(target)
        )).isEmpty();
        assertThat(transcriptQueryService.findSearchableTranscriptContexts(
                notSearchable.assetId(),
                notSearchable.workspaceId(),
                List.of(new CanonicalTranscriptContextTarget("ready-row", 1))
        )).isEmpty();
        assertThat(transcriptQueryService.findSearchableTranscriptContexts(
                notOwned.assetId(),
                notOwned.workspaceId(),
                List.of(new CanonicalTranscriptContextTarget("private-row", 1))
        )).isEmpty();
    }

    private UUID persistAsset(List<AssetTranscriptRowInput> rows) {
        return persistAssetFixture(rows, AssetStatus.SEARCHABLE, "owner-1").assetId();
    }

    private AssetFixture persistAssetFixture(
            List<AssetTranscriptRowInput> rows,
            AssetStatus status,
            String ownerId
    ) {
        Workspace workspace = workspaceStore.save(new Workspace(
                UUID.randomUUID(), "Canonical context", ownerId, false
        ));
        Asset asset = assetStore.save(Asset.uploaded(
                UUID.randomUUID(),
                "lecture.mp4",
                "Lecture",
                status,
                workspace.getId(),
                "workspace-media",
                "objects/" + UUID.randomUUID() + ".mp4",
                "video/mp4",
                42L,
                null
        ));
        transcriptStore.replace(asset.getId(), rows);
        return new AssetFixture(asset.getId(), workspace.getId());
    }

    private AssetTranscriptRowInput row(
            String id,
            int segmentIndex,
            Long startMs,
            Long endMs,
            String text
    ) {
        return new AssetTranscriptRowInput(
                id,
                "video-1",
                segmentIndex,
                startMs,
                endMs,
                text,
                "2026-07-30T00:00:" + String.format("%02d", segmentIndex) + "Z"
        );
    }

    private com.aiknowledgeworkspace.workspacecore.asset.application.model.CanonicalTranscriptContextWindow window(
            List<com.aiknowledgeworkspace.workspacecore.asset.application.model.CanonicalTranscriptContextWindow> windows,
            String rowId
    ) {
        return windows.stream()
                .filter(window -> rowId.equals(window.requestedTranscriptRowId()))
                .findFirst()
                .orElseThrow();
    }

    private record AssetFixture(UUID assetId, UUID workspaceId) {
    }
}
