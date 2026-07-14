package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.AssetSearchIndexJobStatus;
import com.aiknowledgeworkspace.workspacecore.search.indexing.infrastructure.persistence.AssetSearchIndexJobRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetRepository;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.infrastructure.persistence.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-search-index-jobs;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class AssetSearchIndexJobRepositoryTest {

    @Autowired
    private AssetSearchIndexJobRepository searchIndexJobRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        searchIndexJobRepository.deleteAll();
        assetRepository.deleteAll();
        workspaceRepository.deleteAll();
    }

    @Test
    void databaseRejectsDuplicateActiveJobsForSameAssetAndFingerprint() {
        Asset asset = assetRepository.save(asset(UUID.randomUUID()));
        String fingerprint = "same-fingerprint";

        AssetSearchIndexJob firstJob = searchIndexJobRepository.saveAndFlush(new AssetSearchIndexJob(
                asset.getId(),
                fingerprint
        ));

        assertThat(firstJob.getActiveFingerprintKey()).isEqualTo(fingerprint);
        assertThatThrownBy(() -> searchIndexJobRepository.saveAndFlush(new AssetSearchIndexJob(
                asset.getId(),
                fingerprint
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseAllowsHistoricalTerminalJobAndOneCurrentActiveJobForSameFingerprint() {
        Asset asset = assetRepository.save(asset(UUID.randomUUID()));
        String fingerprint = "historical-fingerprint";
        AssetSearchIndexJob indexedJob = new AssetSearchIndexJob(asset.getId(), fingerprint);
        indexedJob.markIndexing();
        indexedJob.markIndexed(Instant.now());
        UUID indexedJobId = searchIndexJobRepository.saveAndFlush(indexedJob).getId();
        entityManager.clear();

        AssetSearchIndexJob activeJob = searchIndexJobRepository.saveAndFlush(new AssetSearchIndexJob(
                asset.getId(),
                fingerprint
        ));

        AssetSearchIndexJob reloadedIndexedJob = searchIndexJobRepository.findById(indexedJobId).orElseThrow();
        assertThat(reloadedIndexedJob.getActiveFingerprintKey()).isNull();
        assertThat(activeJob.getActiveFingerprintKey()).isEqualTo(fingerprint);
        assertThat(activeJob.getStatus()).isEqualTo(AssetSearchIndexJobStatus.PENDING);
    }

    private Asset asset(UUID assetId) {
        Workspace workspace = workspaceRepository.save(new Workspace(
                UUID.randomUUID(),
                "Study Workspace",
                "user-1",
                false
        ));
        return new Asset(
                assetId,
                "lecture.mp4",
                "Lecture",
                AssetStatus.TRANSCRIPT_READY,
                workspace,
                "workspace-media",
                "users/user-1/workspaces/%s/assets/%s/raw/lecture.mp4".formatted(workspace.getId(), assetId),
                "video/mp4",
                123L,
                "\"etag-1\""
        );
    }
}
