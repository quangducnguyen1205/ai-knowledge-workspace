package com.aiknowledgeworkspace.workspacecore.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxRelayService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxMessagePublisher outboxMessagePublisher;
    private final OutboxRelayProperties outboxRelayProperties;
    private final Clock clock;

    @Autowired
    public OutboxRelayService(
            OutboxEventRepository outboxEventRepository,
            OutboxMessagePublisher outboxMessagePublisher,
            OutboxRelayProperties outboxRelayProperties
    ) {
        this(outboxEventRepository, outboxMessagePublisher, outboxRelayProperties, Clock.systemUTC());
    }

    OutboxRelayService(
            OutboxEventRepository outboxEventRepository,
            OutboxMessagePublisher outboxMessagePublisher,
            OutboxRelayProperties outboxRelayProperties,
            Clock clock
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxMessagePublisher = outboxMessagePublisher;
        this.outboxRelayProperties = outboxRelayProperties;
        this.clock = clock;
    }

    @Transactional
    public int relayDueEvents() {
        if (!outboxRelayProperties.isEnabled()) {
            return 0;
        }

        List<OutboxEvent> dueEvents = outboxEventRepository.findDueEvents(
                OutboxEventStatus.PENDING,
                Instant.now(clock),
                PageRequest.of(0, resolvedBatchSize())
        );

        int processedCount = 0;
        for (OutboxEvent event : dueEvents) {
            if (relayEvent(event)) {
                processedCount++;
            }
        }

        return processedCount;
    }

    private boolean relayEvent(OutboxEvent event) {
        int claimedCount = outboxEventRepository.markPublishing(
                event.getId(),
                OutboxEventStatus.PENDING,
                OutboxEventStatus.PUBLISHING
        );
        if (claimedCount != 1) {
            return false;
        }

        OutboxEvent claimedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();

        try {
            outboxMessagePublisher.publish(claimedEvent);
            claimedEvent.markPublished(Instant.now(clock));
        } catch (RuntimeException exception) {
            claimedEvent.recordPublishFailure(
                    resolveErrorMessage(exception),
                    Instant.now(clock).plus(resolvedRetryDelay()),
                    resolvedMaxAttempts()
            );
        }

        outboxEventRepository.save(claimedEvent);
        return true;
    }

    private int resolvedBatchSize() {
        return Math.max(1, outboxRelayProperties.getBatchSize());
    }

    private int resolvedMaxAttempts() {
        return Math.max(1, outboxRelayProperties.getMaxAttempts());
    }

    private Duration resolvedRetryDelay() {
        Duration retryDelay = outboxRelayProperties.getRetryDelay();
        if (retryDelay == null || retryDelay.isNegative()) {
            return Duration.ZERO;
        }
        return retryDelay;
    }

    private String resolveErrorMessage(RuntimeException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getMessage();
    }
}
