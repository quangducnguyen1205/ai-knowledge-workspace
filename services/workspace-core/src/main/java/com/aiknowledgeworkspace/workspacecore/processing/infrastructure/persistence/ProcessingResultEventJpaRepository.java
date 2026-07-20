package com.aiknowledgeworkspace.workspacecore.processing.infrastructure.persistence;

import com.aiknowledgeworkspace.workspacecore.processing.result.ConsumedProcessingResultEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProcessingResultEventJpaRepository extends JpaRepository<ConsumedProcessingResultEvent, UUID> {
}
