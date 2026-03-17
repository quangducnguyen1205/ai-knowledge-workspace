package com.aiknowledgeworkspace.workspacecore.asset;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AssetService {

    public Asset getAsset(UUID assetId) {
        // TODO: replace this stub with persistence and real workspace ownership checks.
        return new Asset(
                assetId,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "placeholder-lecture.mp4",
                AssetStatus.REGISTERED,
                Instant.now()
        );
    }
}
