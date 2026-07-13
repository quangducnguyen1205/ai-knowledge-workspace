package com.aiknowledgeworkspace.workspacecore.processing.smoke;

import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxDeliveryStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxRelay;
import com.aiknowledgeworkspace.workspacecore.outbox.application.RelayRequest;
import com.aiknowledgeworkspace.workspacecore.processing.integration.request.ProcessingRequestedEventContract;
import com.aiknowledgeworkspace.workspacecore.processing.result.ProcessingResultEventHandler;
import com.aiknowledgeworkspace.workspacecore.processing.result.ProcessingResultHandleResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProcessingSmokeCommandRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingSmokeCommandRunner.class);

    private final ProcessingSmokeProperties properties;
    private final OutboxRelay outboxRelay;
    private final ProcessingResultEventHandler processingResultEventHandler;
    private final ConfigurableApplicationContext applicationContext;

    public ProcessingSmokeCommandRunner(
            ProcessingSmokeProperties properties,
            OutboxRelay outboxRelay,
            ProcessingResultEventHandler processingResultEventHandler,
            ConfigurableApplicationContext applicationContext
    ) {
        this.properties = properties;
        this.outboxRelay = outboxRelay;
        this.processingResultEventHandler = processingResultEventHandler;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.getCommand() == ProcessingSmokeCommand.NONE) {
            return;
        }

        try {
            switch (properties.getCommand()) {
                case RELAY_REQUEST_OUTBOX_ONCE -> relayRequestOutboxOnce();
                case HANDLE_RESULT_FILE_ONCE -> handleResultFileOnce();
                case NONE -> {
                }
            }
        } finally {
            applicationContext.close();
        }
    }

    private void relayRequestOutboxOnce() {
        UUID eventId = resolveRequestOutboxEventId();
        OutboxDeliveryStatus status = outboxRelay.relay(RelayRequest.explicit(
                eventId,
                ProcessingRequestedEventContract.EVENT_TYPE,
                "Manual smoke relay only supports asset.processing.requested events"
        )).requiredDeliveryStatus();
        LOGGER.info("Manual smoke request outbox relay completed eventId={} status={}", eventId, status);
        System.out.println("SPRING_SMOKE_REQUEST_RELAY eventId=%s status=%s".formatted(eventId, status));
    }

    private void handleResultFileOnce() {
        Path resultEventFile = resolveResultEventFile();
        String rawEventJson = readResultEventFile(resultEventFile);
        ProcessingResultHandleResult result = processingResultEventHandler.handle(rawEventJson);
        LOGGER.info(
                "Manual smoke processing result handler completed eventId={} status={} applied={}",
                result.eventId(),
                result.status(),
                result.applied()
        );
        System.out.println(
                "SPRING_SMOKE_RESULT_HANDLER eventId=%s status=%s applied=%s".formatted(
                        result.eventId(),
                        result.status(),
                        result.applied()
                )
        );
    }

    private Path resolveResultEventFile() {
        if (!StringUtils.hasText(properties.getResultEventFile())) {
            throw new IllegalStateException(
                    "workspace.processing.smoke.result-event-file is required for HANDLE_RESULT_FILE_ONCE"
            );
        }
        return Path.of(properties.getResultEventFile());
    }

    private UUID resolveRequestOutboxEventId() {
        UUID requestOutboxEventId = properties.getRequestOutboxEventId();
        if (requestOutboxEventId == null) {
            throw new IllegalStateException(
                    "workspace.processing.smoke.request-outbox-event-id is required for RELAY_REQUEST_OUTBOX_ONCE"
            );
        }
        return requestOutboxEventId;
    }

    private String readResultEventFile(Path resultEventFile) {
        try {
            return Files.readString(resultEventFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read processing result event file: " + resultEventFile, exception);
        }
    }
}
