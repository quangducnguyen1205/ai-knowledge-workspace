package com.aiknowledgeworkspace.workspacecore.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByAggregateTypeAndAggregateId(String aggregateType, UUID aggregateId);

    @Query("""
            select event
            from OutboxEvent event
            where event.status = :status
              and (event.nextAttemptAt is null or event.nextAttemptAt <= :now)
            order by event.createdAt asc, event.id asc
            """)
    List<OutboxEvent> findDueEvents(
            @Param("status") OutboxEventStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query("""
            select event.id
            from OutboxEvent event
            where event.status = :status
              and (event.nextAttemptAt is null or event.nextAttemptAt <= :now)
            order by event.createdAt asc, event.id asc
            """)
    List<UUID> findDueEventIds(
            @Param("status") OutboxEventStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query("""
            select event.id
            from OutboxEvent event
            where event.status = :status
              and event.eventType = :eventType
              and (event.nextAttemptAt is null or event.nextAttemptAt <= :now)
            order by event.createdAt asc, event.id asc
            """)
    List<UUID> findDueEventIdsByEventType(
            @Param("status") OutboxEventStatus status,
            @Param("eventType") String eventType,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update OutboxEvent event
            set event.status = :publishingStatus,
                event.nextAttemptAt = null,
                event.updatedAt = :now
            where event.id = :id
              and event.status = :pendingStatus
            """)
    int markPublishing(
            @Param("id") UUID id,
            @Param("pendingStatus") OutboxEventStatus pendingStatus,
            @Param("publishingStatus") OutboxEventStatus publishingStatus,
            @Param("now") Instant now
    );
}
