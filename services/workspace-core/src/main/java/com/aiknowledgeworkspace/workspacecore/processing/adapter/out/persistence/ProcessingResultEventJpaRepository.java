package com.aiknowledgeworkspace.workspacecore.processing.adapter.out.persistence;

import com.aiknowledgeworkspace.workspacecore.processing.domain.ConsumedProcessingResultEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProcessingResultEventJpaRepository extends JpaRepository<ConsumedProcessingResultEvent, UUID> {
}
