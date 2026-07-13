package com.aiknowledgeworkspace.workspacecore.processing.recovery;

import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxDeliveryStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxManualRecovery;
import com.aiknowledgeworkspace.workspacecore.outbox.application.StuckOutboxRecoveryRequest;
import com.aiknowledgeworkspace.workspacecore.processing.integration.request.ProcessingRequestedEventContract;
import com.aiknowledgeworkspace.workspacecore.processing.result.ProcessingResultEventHandler;
import com.aiknowledgeworkspace.workspacecore.processing.result.ProcessingResultHandleResult;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessingRecoveryService {

    private final ProcessingResultEventHandler processingResultEventHandler;
    private final OutboxManualRecovery outboxManualRecovery;

    @Autowired
    public ProcessingRecoveryService(
            ProcessingResultEventHandler processingResultEventHandler,
            OutboxManualRecovery outboxManualRecovery
    ) {
        this.processingResultEventHandler = processingResultEventHandler;
        this.outboxManualRecovery = outboxManualRecovery;
    }

    public ProcessingResultHandleResult retryFailedResultEventOnce(UUID eventId) {
        return processingResultEventHandler.recoverFailedEvent(eventId);
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
