package com.aiknowledgeworkspace.workspacecore.asset;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
import com.aiknowledgeworkspace.workspacecore.search.application.ExplicitIndexingApplication;
import com.aiknowledgeworkspace.workspacecore.search.application.ExplicitIndexingResult;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetQueryApplicationService assetQueryApplicationService;
    private final UploadAssetApplicationService uploadAssetApplicationService;
    private final AssetDeletionService assetDeletionService;
    private final AssetTitleUpdateService assetTitleUpdateService;
    private final ExplicitIndexingApplication explicitIndexingApplication;

    public AssetController(
            AssetQueryApplicationService assetQueryApplicationService,
            UploadAssetApplicationService uploadAssetApplicationService,
            AssetDeletionService assetDeletionService,
            AssetTitleUpdateService assetTitleUpdateService,
            ExplicitIndexingApplication explicitIndexingApplication
    ) {
        this.assetQueryApplicationService = assetQueryApplicationService;
        this.uploadAssetApplicationService = uploadAssetApplicationService;
        this.assetDeletionService = assetDeletionService;
        this.assetTitleUpdateService = assetTitleUpdateService;
        this.explicitIndexingApplication = explicitIndexingApplication;
    }

    @GetMapping
    public AssetListResponse listAssets(
            @RequestParam(value = "workspaceId", required = false) UUID workspaceId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "assetStatus", required = false) AssetStatus assetStatus
    ) {
        return assetQueryApplicationService.listAssets(workspaceId, page, size, assetStatus);
    }

    @GetMapping("/{assetId}")
    public Asset getAsset(@PathVariable UUID assetId) {
        return assetQueryApplicationService.getAsset(assetId);
    }

    @PatchMapping("/{assetId}")
    public Asset updateAssetTitle(
            @PathVariable UUID assetId,
            @RequestBody(required = false) UpdateAssetTitleRequest request
    ) {
        return assetTitleUpdateService.updateAssetTitle(assetId, request);
    }

    @DeleteMapping("/{assetId}")
    public ResponseEntity<Void> deleteAsset(@PathVariable UUID assetId) {
        assetDeletionService.deleteAsset(assetId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/{assetId}/status")
    public AssetStatusResponse getAssetStatus(@PathVariable UUID assetId) {
        return assetQueryApplicationService.getAssetStatus(assetId);
    }

    @GetMapping("/{assetId}/transcript")
    public List<AssetTranscriptRowResponse> getAssetTranscript(@PathVariable UUID assetId) {
        return assetQueryApplicationService.getAssetTranscript(assetId);
    }

    @GetMapping("/{assetId}/transcript/context")
    public AssetTranscriptContextResponse getAssetTranscriptContext(
            @PathVariable UUID assetId,
            @RequestParam("transcriptRowId") String transcriptRowId,
            @RequestParam(value = "window", required = false) Integer window
    ) {
        return assetQueryApplicationService.getAssetTranscriptContext(assetId, transcriptRowId, window);
    }

    @PostMapping("/{assetId}/index")
    public AssetIndexResponse indexAssetTranscript(@PathVariable UUID assetId) {
        ExplicitIndexingResult result = explicitIndexingApplication.indexAssetTranscript(assetId);
        return new AssetIndexResponse(result.assetId(), AssetStatus.SEARCHABLE, result.indexedDocumentCount());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetUploadResponse> uploadAsset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "workspaceId", required = false) UUID workspaceId,
            @RequestParam(value = "title", required = false) String title
    ) {
        AssetUploadResponse response = uploadAssetApplicationService.uploadAsset(workspaceId, file, title);
        return ResponseEntity.accepted().body(response);
    }
}
