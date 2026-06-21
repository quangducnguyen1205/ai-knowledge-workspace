package com.aiknowledgeworkspace.workspacecore.processing.result;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumedProcessingResultEventRepository
        extends JpaRepository<ConsumedProcessingResultEvent, UUID> {
}
