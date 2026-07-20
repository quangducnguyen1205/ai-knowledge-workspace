package com.aiknowledgeworkspace.workspacecore.search.adapter.in.operator;

import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxDeliveryStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxRelay;
import com.aiknowledgeworkspace.workspacecore.outbox.api.RelayRequest;
import com.aiknowledgeworkspace.workspacecore.search.application.model.IndexingRequestedEventContract;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class SearchSmokeCommandRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchSmokeCommandRunner.class);

    private final SearchSmokeProperties properties;
    private final OutboxRelay outboxRelay;
    private final ConfigurableApplicationContext applicationContext;

    public SearchSmokeCommandRunner(
            SearchSmokeProperties properties,
            OutboxRelay outboxRelay,
            ConfigurableApplicationContext applicationContext
    ) {
        this.properties = properties;
        this.outboxRelay = outboxRelay;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.getCommand() == SearchSmokeCommand.NONE) {
            return;
        }

        try {
            if (properties.getCommand() == SearchSmokeCommand.RELAY_INDEXING_OUTBOX_ONCE) {
                relayIndexingOutboxOnce();
            }
        } finally {
            applicationContext.close();
        }
    }

    private void relayIndexingOutboxOnce() {
        UUID eventId = resolveIndexingOutboxEventId();
        OutboxDeliveryStatus status = outboxRelay.relay(RelayRequest.explicit(
                eventId,
                IndexingRequestedEventContract.EVENT_TYPE,
                "Manual search smoke relay only supports asset.indexing.requested events"
        )).requiredDeliveryStatus();
        LOGGER.info("Manual smoke indexing outbox relay completed eventId={} status={}", eventId, status);
        System.out.println("SPRING_SMOKE_INDEXING_RELAY eventId=%s status=%s".formatted(eventId, status));
    }

    private UUID resolveIndexingOutboxEventId() {
        UUID indexingOutboxEventId = properties.getIndexingOutboxEventId();
        if (indexingOutboxEventId == null) {
            throw new IllegalStateException(
                    "workspace.search.smoke.indexing-outbox-event-id is required for RELAY_INDEXING_OUTBOX_ONCE"
            );
        }
        return indexingOutboxEventId;
    }
}
