package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxDraft;
import com.aiknowledgeworkspace.workspacecore.outbox.application.port.out.OutboxEventStore;
import com.aiknowledgeworkspace.workspacecore.processing.api.YouTubeProcessingRequestCommand;
import com.aiknowledgeworkspace.workspacecore.processing.application.port.out.ProcessingJobStore;
import com.aiknowledgeworkspace.workspacecore.processing.application.port.out.ProcessingRequestEventFactory;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccess;
import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;
import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-youtube-create;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class YouTubeAssetCreationTransactionIntegrationTest {

    @Autowired
    private YouTubeAssetCreationTransaction transaction;

    @Autowired
    private WorkspaceStore workspaceStore;

    @Autowired
    private AssetStore assetStore;

    @Autowired
    private ProcessingJobStore processingJobStore;

    @Autowired
    private OutboxEventStore outboxEventStore;

    @MockBean
    private ProcessingRequestEventFactory eventFactory;

    @BeforeEach
    void resetFactory() {
        reset(eventFactory);
    }

    @Test
    void commitsAssetJobAndV2OutboxTogether() {
        Workspace workspace = workspaceStore.save(new Workspace(
                UUID.randomUUID(), "YouTube create", "owner-1", false
        ));
        UUID eventId = UUID.randomUUID();
        when(eventFactory.create(any(YouTubeProcessingRequestCommand.class))).thenAnswer(invocation -> {
            YouTubeProcessingRequestCommand command = invocation.getArgument(0);
            return v2Draft(eventId, command.assetId());
        });

        var result = transaction.persist(
                new WorkspaceAccess(workspace.getId(), "owner-1"),
                "abc_DEF-123",
                "Lecture"
        );

        assertThat(assetStore.findById(result.assetId())).isPresent();
        assertThat(processingJobStore.findByAssetId(result.assetId()))
                .get()
                .satisfies(job -> assertThat(job.getProcessingRequestEventId()).isEqualTo(eventId));
        assertThat(outboxEventStore.findByAggregate("ASSET", result.assetId()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getId()).isEqualTo(eventId);
                    assertThat(event.getEventType()).isEqualTo("asset.processing.requested");
                    assertThat(event.getEventVersion()).isEqualTo(2);
                });
    }

    @Test
    void eventCreationFailureRollsBackAssetJobAndOutbox() {
        Workspace workspace = workspaceStore.save(new Workspace(
                UUID.randomUUID(), "YouTube rollback", "owner-1", false
        ));
        AtomicReference<UUID> attemptedAssetId = new AtomicReference<>();
        when(eventFactory.create(any(YouTubeProcessingRequestCommand.class))).thenAnswer(invocation -> {
            YouTubeProcessingRequestCommand command = invocation.getArgument(0);
            attemptedAssetId.set(command.assetId());
            throw new IllegalStateException("event serialization failed");
        });

        assertThatThrownBy(() -> transaction.persist(
                new WorkspaceAccess(workspace.getId(), "owner-1"),
                "zyx_WVU-987",
                "Rollback"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("event serialization failed");

        UUID assetId = attemptedAssetId.get();
        assertThat(assetId).isNotNull();
        assertThat(assetStore.findById(assetId)).isEmpty();
        assertThat(processingJobStore.findByAssetId(assetId)).isEmpty();
        assertThat(outboxEventStore.findByAggregate("ASSET", assetId)).isEmpty();
    }

    private OutboxDraft v2Draft(UUID eventId, UUID assetId) {
        return new OutboxDraft(
                eventId,
                "asset.processing.requested",
                2,
                "ASSET",
                assetId,
                assetId.toString(),
                """
                        {
                          "assetId": "%s",
                          "workspaceId": null,
                          "ownerId": "owner-1",
                          "sourceType": "YOUTUBE",
                          "youtubeVideoId": "abc_DEF-123",
                          "requestedAt": "2026-07-27T00:00:00Z"
                        }
                        """.formatted(assetId)
        );
    }
}
