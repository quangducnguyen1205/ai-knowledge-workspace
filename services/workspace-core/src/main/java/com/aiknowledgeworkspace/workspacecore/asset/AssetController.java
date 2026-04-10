package com.aiknowledgeworkspace.workspacecore.asset;

import java.util.List;
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
import com.aiknowledgeworkspace.workspacecore.search.TranscriptIndexingService;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService assetService;
    private final TranscriptIndexingService transcriptIndexingService;

    public AssetController(AssetService assetService, TranscriptIndexingService transcriptIndexingService) {
        this.assetService = assetService;
        this.transcriptIndexingService = transcriptIndexingService;
    }

    @GetMapping
    public List<AssetSummaryResponse> listAssets(
            @RequestParam(value = "workspaceId", required = false) UUID workspaceId
    ) {
        return assetService.listAssets(workspaceId);
    }

    @GetMapping("/{assetId}")
    public Asset getAsset(@PathVariable UUID assetId) {
        return assetService.getAsset(assetId);
    }

    @GetMapping("/{assetId}/status")
    public AssetStatusResponse getAssetStatus(@PathVariable UUID assetId) {
        return assetService.getAssetStatus(assetId);
    }

    @GetMapping("/{assetId}/transcript")
    public List<AssetTranscriptRowResponse> getAssetTranscript(@PathVariable UUID assetId) {
        return assetService.getAssetTranscript(assetId);
    }

    @PostMapping("/{assetId}/index")
    public AssetIndexResponse indexAssetTranscript(@PathVariable UUID assetId) {
        return transcriptIndexingService.indexAssetTranscript(assetId);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetUploadResponse> uploadAsset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "workspaceId", required = false) UUID workspaceId,
            @RequestParam(value = "title", required = false) String title
    ) {
        AssetUploadResponse response = assetService.uploadAsset(workspaceId, file, title);
        return ResponseEntity.accepted().body(response);
    }
}
