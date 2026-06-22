package com.aiknowledgeworkspace.workspacecore.search.smoke;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEventStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.OutboxRelayService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

@ExtendWith(MockitoExtension.class)
class SearchSmokeCommandRunnerTest {

    @Mock
    private OutboxRelayService outboxRelayService;

    @Mock
    private ConfigurableApplicationContext applicationContext;

    @Test
    void noneCommandDoesNothingAndKeepsApplicationRunning() {
        SearchSmokeProperties properties = new SearchSmokeProperties();

        newRunner(properties).run(new DefaultApplicationArguments());

        verify(outboxRelayService, never()).relayDueEvents();
        verify(outboxRelayService, never()).relayIndexingEventByIdOnce(any());
        verify(applicationContext, never()).close();
    }

    @Test
    void relayIndexingOutboxCommandRequiresEventIdAndStillClosesApplication() {
        SearchSmokeProperties properties = new SearchSmokeProperties();
        properties.setCommand(SearchSmokeCommand.RELAY_INDEXING_OUTBOX_ONCE);

        assertThatThrownBy(() -> newRunner(properties).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("indexing-outbox-event-id is required");

        verify(outboxRelayService, never()).relayDueEvents();
        verify(outboxRelayService, never()).relayIndexingEventByIdOnce(any());
        verify(applicationContext).close();
    }

    @Test
    void relayIndexingOutboxCommandRelaysOnlyConfiguredEventAndClosesApplication() {
        UUID eventId = UUID.randomUUID();
        SearchSmokeProperties properties = new SearchSmokeProperties();
        properties.setCommand(SearchSmokeCommand.RELAY_INDEXING_OUTBOX_ONCE);
        properties.setIndexingOutboxEventId(eventId);
        when(outboxRelayService.relayIndexingEventByIdOnce(eventId)).thenReturn(OutboxEventStatus.PUBLISHED);

        newRunner(properties).run(new DefaultApplicationArguments());

        verify(outboxRelayService).relayIndexingEventByIdOnce(eventId);
        verify(outboxRelayService, never()).relayDueEvents();
        verify(applicationContext).close();
    }

    private SearchSmokeCommandRunner newRunner(SearchSmokeProperties properties) {
        return new SearchSmokeCommandRunner(properties, outboxRelayService, applicationContext);
    }
}
