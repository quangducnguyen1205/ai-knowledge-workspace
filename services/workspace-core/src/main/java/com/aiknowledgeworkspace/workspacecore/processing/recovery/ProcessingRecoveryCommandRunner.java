package com.aiknowledgeworkspace.workspacecore.processing.recovery;

import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxDeliveryStatus;
import com.aiknowledgeworkspace.workspacecore.processing.result.ProcessingResultHandleResult;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ProcessingRecoveryCommandRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingRecoveryCommandRunner.class);

    private final ProcessingRecoveryProperties properties;
    private final ProcessingRecoveryService processingRecoveryService;
    private final ConfigurableApplicationContext applicationContext;

    public ProcessingRecoveryCommandRunner(
            ProcessingRecoveryProperties properties,
            ProcessingRecoveryService processingRecoveryService,
            ConfigurableApplicationContext applicationContext
    ) {
        this.properties = properties;
        this.processingRecoveryService = processingRecoveryService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.getCommand() == ProcessingRecoveryCommand.NONE) {
            return;
        }

        try {
            switch (properties.getCommand()) {
                case RETRY_FAILED_RESULT_EVENT_ONCE -> retryFailedResultEventOnce();
                case REQUEUE_STUCK_OUTBOX_EVENT_ONCE -> requeueStuckOutboxEventOnce();
                case NONE -> {
                }
            }
        } finally {
            applicationContext.close();
        }
    }

    private void retryFailedResultEventOnce() {
        UUID eventId = resolveResultEventId();
        ProcessingResultHandleResult result = processingRecoveryService.retryFailedResultEventOnce(eventId);
        LOGGER.info(
                "Manual processing result recovery completed eventId={} status={} applied={}",
                result.eventId(),
                result.status(),
                result.applied()
        );
        System.out.println(
                "SPRING_RECOVERY_RESULT_EVENT eventId=%s status=%s applied=%s".formatted(
                        result.eventId(),
                        result.status(),
                        result.applied()
                )
        );
    }

    private void requeueStuckOutboxEventOnce() {
        UUID eventId = resolveOutboxEventId();
        OutboxDeliveryStatus status = processingRecoveryService.requeueStuckOutboxEventOnce(
                eventId,
                properties.getMinimumPublishingAge()
        );
        LOGGER.info("Manual outbox recovery completed eventId={} status={}", eventId, status);
        System.out.println("SPRING_RECOVERY_OUTBOX_REQUEUE eventId=%s status=%s".formatted(eventId, status));
    }

    private UUID resolveResultEventId() {
        UUID resultEventId = properties.getResultEventId();
        if (resultEventId == null) {
            throw new IllegalStateException(
                    "workspace.processing.recovery.result-event-id is required for RETRY_FAILED_RESULT_EVENT_ONCE"
            );
        }
        return resultEventId;
    }

    private UUID resolveOutboxEventId() {
        UUID outboxEventId = properties.getOutboxEventId();
        if (outboxEventId == null) {
            throw new IllegalStateException(
                    "workspace.processing.recovery.outbox-event-id is required for REQUEUE_STUCK_OUTBOX_EVENT_ONCE"
            );
        }
        return outboxEventId;
    }
}
