package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.asset.application.command.SaveAssetPlaybackProgressCommand;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.AssetPlaybackProgressUseCase;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets/{assetId}/playback-progress")
public class AssetPlaybackProgressController {

    private final AssetPlaybackProgressUseCase playbackProgress;

    public AssetPlaybackProgressController(AssetPlaybackProgressUseCase playbackProgress) {
        this.playbackProgress = playbackProgress;
    }

    @GetMapping
    public AssetPlaybackProgressResponse getPlaybackProgress(@PathVariable UUID assetId) {
        return AssetPlaybackProgressResponse.from(playbackProgress.getProgress(assetId));
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public AssetPlaybackProgressResponse savePlaybackProgress(
            @PathVariable UUID assetId,
            @RequestBody(required = false) SaveAssetPlaybackProgressRequest request
    ) {
        return AssetPlaybackProgressResponse.from(playbackProgress.saveProgress(
                assetId,
                new SaveAssetPlaybackProgressCommand(
                        request == null ? null : request.positionMs(),
                        request == null ? null : request.completed()
                )
        ));
    }
}
