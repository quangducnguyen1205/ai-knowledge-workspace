package com.aiknowledgeworkspace.workspacecore.processing.result;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessingResultEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingResultEventHandler.class);
    private static final int MAX_RECOVERABLE_EVENT_JSON_LENGTH = 8192;

    private final ProcessingResultEventParser eventParser;
    private final ProcessingResultInbox processingResultInbox;
    private final ApplyProcessingResultApplicationService applyProcessingResultApplicationService;

    public ProcessingResultEventHandler(
            ProcessingResultEventParser eventParser,
            ProcessingResultInbox processingResultInbox,
            ApplyProcessingResultApplicationService applyProcessingResultApplicationService
    ) {
        this.eventParser = eventParser;
        this.processingResultInbox = processingResultInbox;
        this.applyProcessingResultApplicationService = applyProcessingResultApplicationService;
    }

    @Transactional
    public ProcessingResultHandleResult handle(String rawEventJson) {
        return parseAndApply(rawEventJson, false);
    }

    @Transactional
    public ProcessingResultHandleResult recoverFailedEvent(UUID eventId) {
        String recoverableEventJson = processingResultInbox.requireRecoverableFailedEventJson(eventId);
        ProcessingResultEventEnvelope recoverableEvent = parseEvent(recoverableEventJson);
        if (!eventId.equals(recoverableEvent.eventId())) {
            throw new IllegalStateException("Recoverable processing result event ID did not match requested event ID");
        }
        return parseAndApply(recoverableEventJson, true);
    }

    private ProcessingResultHandleResult parseAndApply(String rawEventJson, boolean manualRecovery) {
        ProcessingResultEventEnvelope event = parseEvent(rawEventJson);
        String recoverableEventJson = eventParser.recoverableEnvelopeJson(rawEventJson, event);
        if (recoverableEventJson.length() > MAX_RECOVERABLE_EVENT_JSON_LENGTH) {
            throw new ProcessingResultEventRejectedException(
                    "Processing result event recoverable envelope exceeded safe length"
            );
        }
        return applyProcessingResultApplicationService.apply(new ApplyProcessingResultCommand(
                event,
                recoverableEventJson,
                manualRecovery
        ));
    }

    private ProcessingResultEventEnvelope parseEvent(String rawEventJson) {
        try {
            return eventParser.parse(rawEventJson);
        } catch (ProcessingResultEventRejectedException exception) {
            LOGGER.warn("Rejected processing result event: {}", exception.getMessage());
            throw exception;
        }
    }
}
