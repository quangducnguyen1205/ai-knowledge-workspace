package com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.DuplicateYouTubeAssetException;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;

@Component
class AssetPersistenceAdapter implements AssetStore {

    private static final String YOUTUBE_WORKSPACE_IDENTITY_CONSTRAINT = "uk_assets_workspace_youtube_video";

    private final AssetJpaRepository assetRepository;

    AssetPersistenceAdapter(AssetJpaRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @Override
    public Optional<Asset> findById(UUID assetId) {
        return assetRepository.findById(assetId);
    }

    @Override
    public Optional<Asset> findByIdForUpdate(UUID assetId) {
        return assetRepository.findByIdForUpdate(assetId);
    }

    @Override
    public List<Asset> findByWorkspaceId(UUID workspaceId) {
        return assetRepository.findByWorkspaceIdOrderByCreatedAtDescIdDesc(workspaceId);
    }

    @Override
    public long countByWorkspaceId(UUID workspaceId) {
        return assetRepository.countByWorkspaceId(workspaceId);
    }

    @Override
    public boolean existsByWorkspaceIdAndYoutubeVideoId(UUID workspaceId, String youtubeVideoId) {
        return assetRepository.existsByWorkspaceIdAndYoutubeVideoId(workspaceId, youtubeVideoId);
    }

    @Override
    public Asset save(Asset asset) {
        return assetRepository.save(asset);
    }

    @Override
    public Asset saveYoutube(Asset asset) {
        try {
            return assetRepository.saveAndFlush(asset);
        } catch (DataIntegrityViolationException exception) {
            if (isYoutubeWorkspaceIdentityConstraint(exception)) {
                throw new DuplicateYouTubeAssetException(exception);
            }
            throw exception;
        }
    }

    @Override
    public void delete(Asset asset) {
        assetRepository.delete(asset);
    }

    private boolean isYoutubeWorkspaceIdentityConstraint(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && isYoutubeWorkspaceIdentityConstraintName(constraintViolation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean isYoutubeWorkspaceIdentityConstraintName(String constraintName) {
        if (constraintName == null) {
            return false;
        }
        String unqualifiedName = constraintName.substring(constraintName.lastIndexOf('.') + 1);
        return YOUTUBE_WORKSPACE_IDENTITY_CONSTRAINT.equalsIgnoreCase(unqualifiedName)
                || unqualifiedName.toLowerCase(java.util.Locale.ROOT)
                .matches(YOUTUBE_WORKSPACE_IDENTITY_CONSTRAINT + "_index_[a-z]+");
    }
}
