package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import com.aiknowledgeworkspace.workspacecore.asset.application.command.CreateYouTubeAssetCommand;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.DuplicateYouTubeAssetException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.InvalidAssetTitleException;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.NormalizedYouTubeUrl;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.YouTubeAssetCreationUseCase;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetProcessingResult;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccess;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccessUseCase;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CreateYouTubeAssetApplicationService implements YouTubeAssetCreationUseCase {

    private static final int MAX_ASSET_TITLE_LENGTH = 255;

    private final WorkspaceAccessUseCase workspaceAccess;
    private final YouTubeUrlPolicy youtubeUrlPolicy;
    private final AssetStore assetStore;
    private final YouTubeAssetCreationTransaction creationTransaction;

    public CreateYouTubeAssetApplicationService(
            WorkspaceAccessUseCase workspaceAccess,
            YouTubeUrlPolicy youtubeUrlPolicy,
            AssetStore assetStore,
            YouTubeAssetCreationTransaction creationTransaction
    ) {
        this.workspaceAccess = workspaceAccess;
        this.youtubeUrlPolicy = youtubeUrlPolicy;
        this.assetStore = assetStore;
        this.creationTransaction = creationTransaction;
    }

    @Override
    public AssetProcessingResult create(CreateYouTubeAssetCommand command) {
        WorkspaceAccess authorizedWorkspace = workspaceAccess.resolveWorkspaceOrDefault(
                command == null ? null : command.workspaceId()
        );
        NormalizedYouTubeUrl source = youtubeUrlPolicy.normalize(command == null ? null : command.url());
        if (assetStore.existsByWorkspaceIdAndYoutubeVideoId(
                authorizedWorkspace.workspaceId(), source.youtubeVideoId()
        )) {
            throw new DuplicateYouTubeAssetException();
        }
        return creationTransaction.persist(
                authorizedWorkspace,
                source.youtubeVideoId(),
                resolveTitle(command.requestedTitle(), source.youtubeVideoId())
        );
    }

    private String resolveTitle(String requestedTitle, String youtubeVideoId) {
        String title = StringUtils.hasText(requestedTitle)
                ? requestedTitle.trim()
                : "YouTube video " + youtubeVideoId;
        if (title.length() > MAX_ASSET_TITLE_LENGTH) {
            throw new InvalidAssetTitleException(
                    "title must be less than or equal to " + MAX_ASSET_TITLE_LENGTH + " characters"
            );
        }
        return title;
    }
}
