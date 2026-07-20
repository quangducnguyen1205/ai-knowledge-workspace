package com.aiknowledgeworkspace.workspacecore.processing.infrastructure.persistence;

import com.aiknowledgeworkspace.workspacecore.processing.domain.ProcessingJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProcessingJobJpaRepository extends JpaRepository<ProcessingJob, UUID> {

    Optional<ProcessingJob> findByAssetId(UUID assetId);

    Optional<ProcessingJob> findByAssetIdAndProcessingRequestEventId(UUID assetId, UUID processingRequestEventId);
}
