package com.aiknowledgeworkspace.workspacecore.processing.adapter.out.persistence;

import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.processing.application.port.out.ProcessingBacklogSnapshot;
import com.aiknowledgeworkspace.workspacecore.processing.domain.ProcessingJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProcessingJobJpaRepository extends JpaRepository<ProcessingJob, UUID> {

    Optional<ProcessingJob> findByAssetId(UUID assetId);

    Optional<ProcessingJob> findByAssetIdAndProcessingRequestEventId(UUID assetId, UUID processingRequestEventId);

    @Query("""
            select new com.aiknowledgeworkspace.workspacecore.processing.application.port.out.ProcessingBacklogSnapshot(
                count(job),
                min(job.updatedAt)
            )
            from ProcessingJob job
            where job.processingJobStatus = :pendingStatus
            """)
    ProcessingBacklogSnapshot loadBacklogSnapshot(@Param("pendingStatus") ProcessingJobStatus pendingStatus);
}
