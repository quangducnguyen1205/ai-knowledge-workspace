package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.module;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetDetails;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetIndexingSource;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetNotFoundException;

import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetSearchabilityService;
import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetTranscriptQueryService;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.IndexingAssetPort;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.IndexingAssetSource;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.IndexingTranscriptRow;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetDetails;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetQueryPort;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetUnavailableException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SearchAssetPortAdapter implements IndexingAssetPort, SearchAssetQueryPort {

    private final AssetTranscriptQueryService transcriptQueryService;
    private final AssetSearchabilityService assetSearchabilityService;

    public SearchAssetPortAdapter(
            AssetTranscriptQueryService transcriptQueryService,
            AssetSearchabilityService assetSearchabilityService
    ) {
        this.transcriptQueryService = transcriptQueryService;
        this.assetSearchabilityService = assetSearchabilityService;
    }

    @Override
    public Optional<IndexingAssetSource> findCurrentIndexingSource(UUID assetId) {
        return transcriptQueryService.findCurrentIndexingSource(assetId).map(this::toSource);
    }

    @Override
    public IndexingAssetSource loadAuthorizedIndexingSource(UUID assetId) {
        try {
            AssetDetails details = transcriptQueryService.getAuthorizedAssetDetails(assetId);
            return toSource(new AssetIndexingSource(
                    details.assetId(),
                    details.workspaceId(),
                    details.title(),
                    transcriptQueryService.loadUsableSnapshot(assetId)
            ));
        } catch (AssetNotFoundException exception) {
            throw new SearchAssetUnavailableException(exception);
        }
    }

    @Override
    public void markTranscriptReady(UUID assetId) {
        try {
            assetSearchabilityService.markTranscriptReady(assetId);
        } catch (AssetNotFoundException exception) {
            throw new SearchAssetUnavailableException(exception);
        }
    }

    @Override
    public void markSearchable(UUID assetId) {
        try {
            assetSearchabilityService.markSearchable(assetId);
        } catch (AssetNotFoundException exception) {
            throw new SearchAssetUnavailableException(exception);
        }
    }

    @Override
    public SearchAssetDetails getAuthorizedAssetDetails(UUID assetId) {
        try {
            AssetDetails details = transcriptQueryService.getAuthorizedAssetDetails(assetId);
            return new SearchAssetDetails(details.assetId(), details.workspaceId(), details.searchable());
        } catch (AssetNotFoundException exception) {
            throw new SearchAssetUnavailableException(exception);
        }
    }

    @Override
    public List<UUID> findSearchableAssetIdsInWorkspace(UUID workspaceId) {
        return transcriptQueryService.findSearchableAssetIdsInWorkspace(workspaceId);
    }

    private IndexingAssetSource toSource(AssetIndexingSource source) {
        return new IndexingAssetSource(
                source.assetId(),
                source.workspaceId(),
                source.assetTitle(),
                source.transcriptRows().stream().map(row -> new IndexingTranscriptRow(
                        row.id(), row.videoId(), row.segmentIndex(), row.startMs(), row.endMs(),
                        row.text(), row.createdAt()
                )).toList()
        );
    }
}
