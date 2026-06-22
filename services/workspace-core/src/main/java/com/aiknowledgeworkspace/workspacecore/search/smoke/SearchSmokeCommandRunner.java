package com.aiknowledgeworkspace.workspacecore.search.smoke;

import com.aiknowledgeworkspace.workspacecore.outbox.OutboxEventStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.OutboxRelayService;
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
    private final OutboxRelayService outboxRelayService;
    private final ConfigurableApplicationContext applicationContext;

    public SearchSmokeCommandRunner(
            SearchSmokeProperties properties,
            OutboxRelayService outboxRelayService,
            ConfigurableApplicationContext applicationContext
    ) {
        this.properties = properties;
        this.outboxRelayService = outboxRelayService;
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
        OutboxEventStatus status = outboxRelayService.relayIndexingEventByIdOnce(eventId);
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
