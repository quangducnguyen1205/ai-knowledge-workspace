package com.aiknowledgeworkspace.workspacecore.processing.smoke;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxDeliveryStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxRelay;
import com.aiknowledgeworkspace.workspacecore.outbox.application.RelayOutcome;
import com.aiknowledgeworkspace.workspacecore.outbox.application.RelayRequest;
import com.aiknowledgeworkspace.workspacecore.processing.integration.request.ProcessingRequestedEventContract;
import com.aiknowledgeworkspace.workspacecore.processing.result.ConsumedProcessingResultEventStatus;
import com.aiknowledgeworkspace.workspacecore.processing.result.ProcessingResultEventHandler;
import com.aiknowledgeworkspace.workspacecore.processing.result.ProcessingResultHandleResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

@ExtendWith(MockitoExtension.class)
class ProcessingSmokeCommandRunnerTest {

    @Mock
    private OutboxRelay outboxRelay;

    @Mock
    private ProcessingResultEventHandler processingResultEventHandler;

    @Mock
    private ConfigurableApplicationContext applicationContext;

    @TempDir
    private Path tempDir;

    @Test
    void noneCommandDoesNothingAndKeepsApplicationRunning() {
        ProcessingSmokeProperties properties = new ProcessingSmokeProperties();

        newRunner(properties).run(new DefaultApplicationArguments());

        verifyNoInteractions(outboxRelay);
        verify(processingResultEventHandler, never()).handle(org.mockito.ArgumentMatchers.anyString());
        verify(applicationContext, never()).close();
    }

    @Test
    void relayRequestOutboxCommandRequiresEventIdAndStillClosesApplication() {
        ProcessingSmokeProperties properties = new ProcessingSmokeProperties();
        properties.setCommand(ProcessingSmokeCommand.RELAY_REQUEST_OUTBOX_ONCE);

        assertThatThrownBy(() -> newRunner(properties).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("request-outbox-event-id is required");

        verifyNoInteractions(outboxRelay);
        verify(applicationContext).close();
    }

    @Test
    void relayRequestOutboxCommandRelaysOnlyConfiguredEventAndClosesApplication() {
        UUID eventId = UUID.randomUUID();
        ProcessingSmokeProperties properties = new ProcessingSmokeProperties();
        properties.setCommand(ProcessingSmokeCommand.RELAY_REQUEST_OUTBOX_ONCE);
        properties.setRequestOutboxEventId(eventId);
        RelayRequest request = RelayRequest.explicit(
                eventId,
                ProcessingRequestedEventContract.EVENT_TYPE,
                "Manual smoke relay only supports asset.processing.requested events"
        );
        when(outboxRelay.relay(request)).thenReturn(RelayOutcome.single(OutboxDeliveryStatus.PUBLISHED));

        newRunner(properties).run(new DefaultApplicationArguments());

        verify(outboxRelay).relay(request);
        verify(applicationContext).close();
    }

    @Test
    void handleResultFileCommandReadsFileRunsHandlerAndClosesApplication() throws Exception {
        Path resultFile = tempDir.resolve("result-event.json");
        String rawEvent = "{\"eventId\":\"%s\"}".formatted(UUID.randomUUID());
        Files.writeString(resultFile, rawEvent);
        UUID eventId = UUID.randomUUID();
        ProcessingSmokeProperties properties = new ProcessingSmokeProperties();
        properties.setCommand(ProcessingSmokeCommand.HANDLE_RESULT_FILE_ONCE);
        properties.setResultEventFile(resultFile.toString());
        when(processingResultEventHandler.handle(rawEvent)).thenReturn(new ProcessingResultHandleResult(
                eventId,
                ConsumedProcessingResultEventStatus.APPLIED,
                true
        ));

        newRunner(properties).run(new DefaultApplicationArguments());

        verify(processingResultEventHandler).handle(rawEvent);
        verify(applicationContext).close();
    }

    @Test
    void handleResultFileCommandRequiresFilePathAndStillClosesApplication() {
        ProcessingSmokeProperties properties = new ProcessingSmokeProperties();
        properties.setCommand(ProcessingSmokeCommand.HANDLE_RESULT_FILE_ONCE);

        assertThatThrownBy(() -> newRunner(properties).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("result-event-file is required");

        verify(applicationContext).close();
    }

    private ProcessingSmokeCommandRunner newRunner(ProcessingSmokeProperties properties) {
        return new ProcessingSmokeCommandRunner(
                properties,
                outboxRelay,
                processingResultEventHandler,
                applicationContext
        );
    }
}
