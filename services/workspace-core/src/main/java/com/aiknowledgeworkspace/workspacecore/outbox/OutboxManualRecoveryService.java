package com.aiknowledgeworkspace.workspacecore.outbox;

import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxDeliveryStatus;
import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxManualRecovery;
import com.aiknowledgeworkspace.workspacecore.outbox.application.StuckOutboxRecoveryRequest;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
class OutboxManualRecoveryService implements OutboxManualRecovery {

    private final OutboxEventRepository repository;
    private final Clock clock;

    @Autowired
    OutboxManualRecoveryService(OutboxEventRepository repository) {
        this(repository, Clock.systemUTC());
    }

    OutboxManualRecoveryService(OutboxEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public OutboxDeliveryStatus requeueStuckPublishing(StuckOutboxRecoveryRequest request) {
        OutboxEvent event = repository.findById(request.eventId())
                .orElseThrow(() -> new IllegalStateException("Outbox event was not found: " + request.eventId()));
        validate(event, request, Instant.now(clock));
        event.requeueFromPublishing();
        repository.save(event);
        return OutboxDeliveryStatus.valueOf(event.getStatus().name());
    }

    private void validate(OutboxEvent event, StuckOutboxRecoveryRequest request, Instant now) {
        if (!request.requiredEventType().equals(event.getEventType())) {
            throw new IllegalStateException(request.eventTypeMismatchMessage() + ": " + event.getId());
        }
        if (event.getStatus() == OutboxEventStatus.PUBLISHED) {
            throw new IllegalStateException("Outbox event is already published: " + event.getId());
        }
        if (event.getStatus() != OutboxEventStatus.PUBLISHING) {
            throw new IllegalStateException(
                    "Outbox event is not stuck in PUBLISHING: " + event.getId() + " status=" + event.getStatus()
            );
        }
        Instant publishingSince = event.getUpdatedAt() == null ? event.getCreatedAt() : event.getUpdatedAt();
        if (publishingSince.plus(request.minimumPublishingAge()).isAfter(now)) {
            throw new IllegalStateException(
                    "Outbox event is not old enough for PUBLISHING recovery: "
                            + event.getId()
                            + " publishingSince="
                            + publishingSince
                            + " minimumAge="
                            + request.minimumPublishingAge()
            );
        }
    }
}
