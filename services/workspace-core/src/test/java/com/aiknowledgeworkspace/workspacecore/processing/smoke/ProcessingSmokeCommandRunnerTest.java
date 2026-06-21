package com.aiknowledgeworkspace.workspacecore.processing.smoke;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.outbox.OutboxRelayService;
import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEventStatus;
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
    private OutboxRelayService outboxRelayService;

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

        verify(outboxRelayService, never()).relayDueEvents();
        verify(outboxRelayService, never()).relayEventByIdOnce(any());
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

        verify(outboxRelayService, never()).relayDueEvents();
        verify(outboxRelayService, never()).relayEventByIdOnce(any());
        verify(applicationContext).close();
    }

    @Test
    void relayRequestOutboxCommandRelaysOnlyConfiguredEventAndClosesApplication() {
        UUID eventId = UUID.randomUUID();
        ProcessingSmokeProperties properties = new ProcessingSmokeProperties();
        properties.setCommand(ProcessingSmokeCommand.RELAY_REQUEST_OUTBOX_ONCE);
        properties.setRequestOutboxEventId(eventId);
        when(outboxRelayService.relayEventByIdOnce(eventId)).thenReturn(OutboxEventStatus.PUBLISHED);

        newRunner(properties).run(new DefaultApplicationArguments());

        verify(outboxRelayService).relayEventByIdOnce(eventId);
        verify(outboxRelayService, never()).relayDueEvents();
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
                outboxRelayService,
                processingResultEventHandler,
                applicationContext
        );
    }
}
