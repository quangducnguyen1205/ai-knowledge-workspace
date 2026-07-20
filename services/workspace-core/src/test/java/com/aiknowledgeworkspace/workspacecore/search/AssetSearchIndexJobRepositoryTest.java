package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.AssetSearchIndexJobStatus;
import com.aiknowledgeworkspace.workspacecore.search.indexing.application.port.out.SearchIndexJobStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-search-index-jobs;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Transactional
class SearchIndexJobStoreTest {

    @Autowired
    private SearchIndexJobStore searchIndexJobRepository;

    @Autowired
    private AssetStore assetRepository;

    @Autowired
    private WorkspaceStore workspaceRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void databaseRejectsDuplicateActiveJobsForSameAssetAndFingerprint() {
        Asset asset = assetRepository.save(asset(UUID.randomUUID()));
        String fingerprint = "same-fingerprint";

        AssetSearchIndexJob firstJob = searchIndexJobRepository.save(new AssetSearchIndexJob(
                asset.getId(),
                fingerprint
        ));
        entityManager.flush();

        assertThat(firstJob.getActiveFingerprintKey()).isEqualTo(fingerprint);
        assertThatThrownBy(() -> {
            searchIndexJobRepository.save(new AssetSearchIndexJob(asset.getId(), fingerprint));
            entityManager.flush();
        }).isInstanceOfAny(DataIntegrityViolationException.class, ConstraintViolationException.class);
    }

    @Test
    void databaseAllowsHistoricalTerminalJobAndOneCurrentActiveJobForSameFingerprint() {
        Asset asset = assetRepository.save(asset(UUID.randomUUID()));
        String fingerprint = "historical-fingerprint";
        AssetSearchIndexJob indexedJob = new AssetSearchIndexJob(asset.getId(), fingerprint);
        indexedJob.markIndexing();
        indexedJob.markIndexed(Instant.now());
        UUID indexedJobId = searchIndexJobRepository.save(indexedJob).getId();
        entityManager.flush();
        entityManager.clear();

        AssetSearchIndexJob activeJob = searchIndexJobRepository.save(new AssetSearchIndexJob(
                asset.getId(),
                fingerprint
        ));
        entityManager.flush();

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
                workspace.getId(),
                "workspace-media",
                "users/user-1/workspaces/%s/assets/%s/raw/lecture.mp4".formatted(workspace.getId(), assetId),
                "video/mp4",
                123L,
                "\"etag-1\""
        );
    }
}
