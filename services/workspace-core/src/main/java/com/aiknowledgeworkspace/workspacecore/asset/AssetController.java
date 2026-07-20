package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.AssetCommandUseCase;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.AssetQueryUseCase;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.AssetUploadUseCase;
import com.aiknowledgeworkspace.workspacecore.asset.application.query.AssetPage;
import com.aiknowledgeworkspace.workspacecore.asset.application.query.AssetStatusView;
import com.aiknowledgeworkspace.workspacecore.asset.application.upload.AssetUploadCommand;
import com.aiknowledgeworkspace.workspacecore.asset.application.upload.AssetUploadResult;

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

    private final AssetQueryUseCase assetQueries;
    private final AssetUploadUseCase assetUpload;
    private final AssetCommandUseCase assetCommands;
    private final ExplicitIndexingApplication explicitIndexingApplication;

    public AssetController(
            AssetQueryUseCase assetQueries,
            AssetUploadUseCase assetUpload,
            AssetCommandUseCase assetCommands,
            ExplicitIndexingApplication explicitIndexingApplication
    ) {
        this.assetQueries = assetQueries;
        this.assetUpload = assetUpload;
        this.assetCommands = assetCommands;
        this.explicitIndexingApplication = explicitIndexingApplication;
    }

    @GetMapping
    public AssetListResponse listAssets(
            @RequestParam(value = "workspaceId", required = false) UUID workspaceId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "assetStatus", required = false) AssetStatus assetStatus
    ) {
        AssetPage result = assetQueries.listAssets(workspaceId, page, size, assetStatus);
        return new AssetListResponse(
                result.items().stream()
                        .map(item -> new AssetSummaryResponse(
                                item.id(), item.title(), item.status(), item.workspaceId(), item.createdAt()
                        ))
                        .toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.hasNext()
        );
    }

    @GetMapping("/{assetId}")
    public AssetResponse getAsset(@PathVariable UUID assetId) {
        return AssetResponse.from(assetQueries.getAsset(assetId));
    }

    @PatchMapping("/{assetId}")
    public AssetResponse updateAssetTitle(
            @PathVariable UUID assetId,
            @RequestBody(required = false) UpdateAssetTitleRequest request
    ) {
        return AssetResponse.from(assetCommands.updateTitle(assetId, request == null ? null : request.title()));
    }

    @DeleteMapping("/{assetId}")
    public ResponseEntity<Void> deleteAsset(@PathVariable UUID assetId) {
        assetCommands.delete(assetId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/{assetId}/status")
    public AssetStatusResponse getAssetStatus(@PathVariable UUID assetId) {
        AssetStatusView result = assetQueries.getAssetStatus(assetId);
        return new AssetStatusResponse(
                result.assetId(), result.processingJobId(), result.assetStatus(), result.processingStatus()
        );
    }

    @GetMapping("/{assetId}/transcript")
    public List<AssetTranscriptRowResponse> getAssetTranscript(@PathVariable UUID assetId) {
        return assetQueries.getAssetTranscript(assetId).stream()
                .map(row -> new AssetTranscriptRowResponse(
                        row.id(), row.videoId(), row.segmentIndex(), row.text(), row.createdAt()
                ))
                .toList();
    }

    @GetMapping("/{assetId}/transcript/context")
    public AssetTranscriptContextResponse getAssetTranscriptContext(
            @PathVariable UUID assetId,
            @RequestParam("transcriptRowId") String transcriptRowId,
            @RequestParam(value = "window", required = false) Integer window
    ) {
        AssetTranscriptContext result = assetQueries.getAssetTranscriptContext(assetId, transcriptRowId, window);
        return new AssetTranscriptContextResponse(
                result.assetId(),
                result.transcriptRowId(),
                result.hitSegmentIndex(),
                result.window(),
                result.rows().stream()
                        .map(row -> new AssetTranscriptRowResponse(
                                row.id(), row.videoId(), row.segmentIndex(), row.text(), row.createdAt()
                        ))
                        .toList()
        );
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
        AssetUploadResult result = assetUpload.upload(new AssetUploadCommand(
                workspaceId,
                file == null ? null : file.getOriginalFilename(),
                file == null ? null : file.getContentType(),
                file == null ? 0L : file.getSize(),
                title,
                file == null ? null : file::getInputStream
        ));
        AssetUploadResponse response = new AssetUploadResponse(
                result.assetId(), result.processingJobId(), result.status(), result.workspaceId()
        );
        return ResponseEntity.accepted().body(response);
    }
}
