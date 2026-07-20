package com.aiknowledgeworkspace.workspacecore.search.adapter.in.operator;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxDeliveryStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxRelay;
import com.aiknowledgeworkspace.workspacecore.outbox.api.RelayOutcome;
import com.aiknowledgeworkspace.workspacecore.outbox.api.RelayRequest;
import com.aiknowledgeworkspace.workspacecore.search.application.model.IndexingRequestedEventContract;
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
    private OutboxRelay outboxRelay;

    @Mock
    private ConfigurableApplicationContext applicationContext;

    @Test
    void noneCommandDoesNothingAndKeepsApplicationRunning() {
        SearchSmokeProperties properties = new SearchSmokeProperties();

        newRunner(properties).run(new DefaultApplicationArguments());

        verifyNoInteractions(outboxRelay);
        verify(applicationContext, never()).close();
    }

    @Test
    void relayIndexingOutboxCommandRequiresEventIdAndStillClosesApplication() {
        SearchSmokeProperties properties = new SearchSmokeProperties();
        properties.setCommand(SearchSmokeCommand.RELAY_INDEXING_OUTBOX_ONCE);

        assertThatThrownBy(() -> newRunner(properties).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("indexing-outbox-event-id is required");

        verifyNoInteractions(outboxRelay);
        verify(applicationContext).close();
    }

    @Test
    void relayIndexingOutboxCommandRelaysOnlyConfiguredEventAndClosesApplication() {
        UUID eventId = UUID.randomUUID();
        SearchSmokeProperties properties = new SearchSmokeProperties();
        properties.setCommand(SearchSmokeCommand.RELAY_INDEXING_OUTBOX_ONCE);
        properties.setIndexingOutboxEventId(eventId);
        RelayRequest request = RelayRequest.explicit(
                eventId,
                IndexingRequestedEventContract.EVENT_TYPE,
                "Manual search smoke relay only supports asset.indexing.requested events"
        );
        when(outboxRelay.relay(request)).thenReturn(RelayOutcome.single(OutboxDeliveryStatus.PUBLISHED));

        newRunner(properties).run(new DefaultApplicationArguments());

        verify(outboxRelay).relay(request);
        verify(applicationContext).close();
    }

    private SearchSmokeCommandRunner newRunner(SearchSmokeProperties properties) {
        return new SearchSmokeCommandRunner(properties, outboxRelay, applicationContext);
    }
}
