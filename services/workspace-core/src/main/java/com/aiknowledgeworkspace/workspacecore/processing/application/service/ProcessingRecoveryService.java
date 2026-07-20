package com.aiknowledgeworkspace.workspacecore.processing.application.service;

import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxDeliveryStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxManualRecovery;
import com.aiknowledgeworkspace.workspacecore.outbox.api.StuckOutboxRecoveryRequest;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingRequestedEventContract;
import com.aiknowledgeworkspace.workspacecore.processing.application.port.in.ProcessingResultUseCase;
import com.aiknowledgeworkspace.workspacecore.processing.application.model.ProcessingResultHandleResult;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessingRecoveryService {

    private final ProcessingResultUseCase processingResultUseCase;
    private final OutboxManualRecovery outboxManualRecovery;

    @Autowired
    public ProcessingRecoveryService(
            ProcessingResultUseCase processingResultUseCase,
            OutboxManualRecovery outboxManualRecovery
    ) {
        this.processingResultUseCase = processingResultUseCase;
        this.outboxManualRecovery = outboxManualRecovery;
    }

    public ProcessingResultHandleResult retryFailedResultEventOnce(UUID eventId) {
        return processingResultUseCase.recoverFailedEvent(eventId);
    }

    @Transactional
    public OutboxDeliveryStatus requeueStuckOutboxEventOnce(UUID eventId, Duration minimumPublishingAge) {
        Duration resolvedMinimumAge = resolvedMinimumPublishingAge(minimumPublishingAge);
        return outboxManualRecovery.requeueStuckPublishing(new StuckOutboxRecoveryRequest(
                eventId,
                ProcessingRequestedEventContract.EVENT_TYPE,
                resolvedMinimumAge,
                "Manual outbox recovery only supports asset.processing.requested events"
        ));
    }

    private Duration resolvedMinimumPublishingAge(Duration minimumPublishingAge) {
        if (minimumPublishingAge == null) {
            throw new IllegalStateException("workspace.processing.recovery.minimum-publishing-age is required");
        }
        if (minimumPublishingAge.isNegative()) {
            throw new IllegalStateException("workspace.processing.recovery.minimum-publishing-age must not be negative");
        }
        return minimumPublishingAge;
    }
}
