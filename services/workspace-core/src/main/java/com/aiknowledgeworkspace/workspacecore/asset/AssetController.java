package com.aiknowledgeworkspace.workspacecore.asset;

import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping("/{assetId}")
    public Asset getAsset(@PathVariable UUID assetId) {
        return assetService.getAsset(assetId);
    }

    @GetMapping("/{assetId}/status")
    public AssetStatusResponse getAssetStatus(@PathVariable UUID assetId) {
        return assetService.getAssetStatus(assetId);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetUploadResponse> uploadAsset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title
    ) {
        AssetUploadResponse response = assetService.uploadAsset(file, title);
        return ResponseEntity.accepted().body(response);
    }
}
