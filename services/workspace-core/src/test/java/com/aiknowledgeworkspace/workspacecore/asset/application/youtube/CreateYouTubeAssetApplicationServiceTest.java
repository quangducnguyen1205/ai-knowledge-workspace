package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.application.command.CreateYouTubeAssetCommand;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.DuplicateYouTubeAssetException;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.NormalizedYouTubeUrl;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetProcessingResult;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetSourceType;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccess;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccessUseCase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateYouTubeAssetApplicationServiceTest {

    private final WorkspaceAccessUseCase workspaceAccess = mock(WorkspaceAccessUseCase.class);
    private final YouTubeUrlPolicy urlPolicy = mock(YouTubeUrlPolicy.class);
    private final AssetStore assetStore = mock(AssetStore.class);
    private final YouTubeAssetCreationTransaction transaction = mock(YouTubeAssetCreationTransaction.class);
    private final CreateYouTubeAssetApplicationService service = new CreateYouTubeAssetApplicationService(
            workspaceAccess, urlPolicy, assetStore, transaction
    );

    @Test
    void authorizesThenNormalizesAndCreatesWithTrimmedTitle() {
        UUID workspaceId = UUID.randomUUID();
        WorkspaceAccess authorized = new WorkspaceAccess(workspaceId, "owner-1");
        NormalizedYouTubeUrl normalized = new NormalizedYouTubeUrl(
                "abc_DEF-123", "https://www.youtube.com/watch?v=abc_DEF-123"
        );
        AssetProcessingResult expected = new AssetProcessingResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AssetStatus.PROCESSING,
                workspaceId,
                AssetSourceType.YOUTUBE,
                "abc_DEF-123"
        );
        when(workspaceAccess.resolveWorkspaceOrDefault(workspaceId)).thenReturn(authorized);
        when(urlPolicy.normalize("https://youtu.be/abc_DEF-123?t=42")).thenReturn(normalized);
        when(transaction.persist(authorized, "abc_DEF-123", "Lecture")).thenReturn(expected);

        AssetProcessingResult result = service.create(new CreateYouTubeAssetCommand(
                workspaceId, "https://youtu.be/abc_DEF-123?t=42", "  Lecture  "
        ));

        assertThat(result).isEqualTo(expected);
        var order = inOrder(workspaceAccess, urlPolicy, assetStore, transaction);
        order.verify(workspaceAccess).resolveWorkspaceOrDefault(workspaceId);
        order.verify(urlPolicy).normalize("https://youtu.be/abc_DEF-123?t=42");
        order.verify(assetStore).existsByWorkspaceIdAndYoutubeVideoId(workspaceId, "abc_DEF-123");
        order.verify(transaction).persist(authorized, "abc_DEF-123", "Lecture");
    }

    @Test
    void missingTitleUsesDeterministicFallbackWithoutProviderLookup() {
        UUID workspaceId = UUID.randomUUID();
        WorkspaceAccess authorized = new WorkspaceAccess(workspaceId, "owner-1");
        when(workspaceAccess.resolveWorkspaceOrDefault(null)).thenReturn(authorized);
        when(urlPolicy.normalize("https://youtu.be/abc_DEF-123")).thenReturn(
                new NormalizedYouTubeUrl("abc_DEF-123", "https://www.youtube.com/watch?v=abc_DEF-123")
        );

        service.create(new CreateYouTubeAssetCommand(null, "https://youtu.be/abc_DEF-123", " "));

        verify(transaction).persist(authorized, "abc_DEF-123", "YouTube video abc_DEF-123");
    }

    @Test
    void existingIdentityInTheAuthorizedWorkspaceConflictsEvenIfItsAssetFailed() {
        UUID workspaceId = UUID.randomUUID();
        WorkspaceAccess authorized = new WorkspaceAccess(workspaceId, "owner-1");
        when(workspaceAccess.resolveWorkspaceOrDefault(workspaceId)).thenReturn(authorized);
        when(urlPolicy.normalize("https://youtu.be/abc_DEF-123")).thenReturn(
                new NormalizedYouTubeUrl("abc_DEF-123", "https://www.youtube.com/watch?v=abc_DEF-123")
        );
        when(assetStore.existsByWorkspaceIdAndYoutubeVideoId(workspaceId, "abc_DEF-123")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateYouTubeAssetCommand(
                workspaceId, "https://youtu.be/abc_DEF-123", null
        )))
                .isInstanceOf(DuplicateYouTubeAssetException.class);

        verifyNoInteractions(transaction);
    }
}
