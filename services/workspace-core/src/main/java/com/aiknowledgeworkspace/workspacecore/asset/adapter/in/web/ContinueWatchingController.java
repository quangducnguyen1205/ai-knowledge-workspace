package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.ContinueWatchingUseCase;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/playback-progress")
public class ContinueWatchingController {

    private final ContinueWatchingUseCase continueWatching;

    public ContinueWatchingController(ContinueWatchingUseCase continueWatching) {
        this.continueWatching = continueWatching;
    }

    @GetMapping
    public ContinueWatchingResponse list(
            @RequestParam(value = "workspaceId", required = false) UUID workspaceId
    ) {
        return ContinueWatchingResponse.from(continueWatching.listForWorkspace(workspaceId));
    }
}
