package com.aiknowledgeworkspace.workspacecore.processing.application.service;

import com.aiknowledgeworkspace.workspacecore.processing.application.model.ProcessingResultHandleResult;

import com.aiknowledgeworkspace.workspacecore.processing.application.model.ProcessingResultEventRejectedException;
import com.aiknowledgeworkspace.workspacecore.processing.application.port.in.ProcessingResultUseCase;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessingResultApplicationService implements ProcessingResultUseCase {

    private static final int MAX_RECOVERABLE_EVENT_JSON_LENGTH = 8192;

    private final ProcessingResultEventParser eventParser;
    private final ProcessingResultInbox processingResultInbox;
    private final ApplyProcessingResultApplicationService applyProcessingResultApplicationService;

    public ProcessingResultApplicationService(
            ProcessingResultEventParser eventParser,
            ProcessingResultInbox processingResultInbox,
            ApplyProcessingResultApplicationService applyProcessingResultApplicationService
    ) {
        this.eventParser = eventParser;
        this.processingResultInbox = processingResultInbox;
        this.applyProcessingResultApplicationService = applyProcessingResultApplicationService;
    }

    @Transactional
    @Override
    public ProcessingResultHandleResult handle(String rawEventJson) {
        return parseAndApply(rawEventJson, false);
    }

    @Transactional
    @Override
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
        return eventParser.parse(rawEventJson);
    }
}
