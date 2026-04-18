package com.aiknowledgeworkspace.workspacecore.workspace;

import com.aiknowledgeworkspace.workspacecore.asset.AssetRepository;
import com.aiknowledgeworkspace.workspacecore.common.identity.CurrentUserService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WorkspaceService {

    private static final int MAX_WORKSPACE_NAME_LENGTH = 255;

    private final WorkspaceRepository workspaceRepository;
    private final AssetRepository assetRepository;
    private final WorkspaceProperties workspaceProperties;
    private final CurrentUserService currentUserService;
    private final DefaultWorkspaceCreationExecutor defaultWorkspaceCreationExecutor;

    @Autowired
    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            AssetRepository assetRepository,
            WorkspaceProperties workspaceProperties,
            CurrentUserService currentUserService,
            DefaultWorkspaceCreationExecutor defaultWorkspaceCreationExecutor
    ) {
        this.workspaceRepository = workspaceRepository;
        this.assetRepository = assetRepository;
        this.workspaceProperties = workspaceProperties;
        this.currentUserService = currentUserService;
        this.defaultWorkspaceCreationExecutor = defaultWorkspaceCreationExecutor;
    }

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            AssetRepository assetRepository,
            WorkspaceProperties workspaceProperties,
            CurrentUserService currentUserService
    ) {
        this(
                workspaceRepository,
                assetRepository,
                workspaceProperties,
                currentUserService,
                workspaceRepository::save
        );
    }

    @Transactional
    public Workspace createWorkspace(String name) {
        return workspaceRepository.save(new Workspace(
                null,
                normalizeWorkspaceName(name),
                currentUserService.getCurrentUserId(),
                false
        ));
    }

    @Transactional
    public List<Workspace> listWorkspaces() {
        String currentUserId = currentUserService.getCurrentUserId();
        ensureDefaultWorkspace(currentUserId);
        return workspaceRepository.findByOwnerId(currentUserId, workspaceListSort());
    }

    @Transactional
    public Workspace getWorkspace(UUID workspaceId) {
        return resolveWorkspaceOrDefault(workspaceId);
    }

    @Transactional
    public Workspace updateWorkspace(UUID workspaceId, String name) {
        String normalizedName = normalizeWorkspaceName(name);
        Workspace workspace = resolveWorkspaceOrDefault(workspaceId);
        workspace.setName(normalizedName);
        return workspaceRepository.save(workspace);
    }

    @Transactional
    public void deleteWorkspace(UUID workspaceId) {
        Workspace workspace = resolveWorkspaceOrDefault(workspaceId);

        if (workspace.isDefaultWorkspace()) {
            throw new WorkspaceDeleteConflictException(
                    "DEFAULT_WORKSPACE_DELETE_FORBIDDEN",
                    "Default workspace cannot be deleted"
            );
        }

        if (assetRepository.countByWorkspace_Id(workspace.getId()) > 0) {
            throw new WorkspaceDeleteConflictException(
                    "WORKSPACE_NOT_EMPTY",
                    "Workspace cannot be deleted while it still contains assets"
            );
        }

        workspaceRepository.delete(workspace);
    }

    public boolean isDefaultWorkspace(Workspace workspace) {
        return workspace != null && workspace.isDefaultWorkspace();
    }

    public boolean isOwnedByCurrentUser(Workspace workspace) {
        return workspace != null
                && StringUtils.hasText(workspace.getOwnerId())
                && currentUserService.getCurrentUserId().equals(workspace.getOwnerId());
    }

    public boolean canAccessLegacyNullWorkspaceAssets() {
        return currentUserService.isDefaultUser(currentUserService.getCurrentUserId());
    }

    public boolean shouldIncludeLegacyNullWorkspaceAssets(Workspace workspace) {
        return isDefaultWorkspace(workspace)
                && canAccessLegacyNullWorkspaceAssets()
                && isOwnedByCurrentUser(workspace);
    }

    @Transactional
    public Workspace resolveWorkspaceOrDefault(UUID workspaceId) {
        String currentUserId = currentUserService.getCurrentUserId();
        if (workspaceId == null) {
            return ensureDefaultWorkspace(currentUserId);
        }

        return workspaceRepository.findByIdAndOwnerId(workspaceId, currentUserId)
                .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
    }

    @Transactional
    public Workspace ensureDefaultWorkspace() {
        return ensureDefaultWorkspace(currentUserService.getCurrentUserId());
    }

    @Transactional
    public Workspace ensureDefaultWorkspace(String currentUserId) {
        return findOwnedDefaultWorkspace(currentUserId)
                .orElseGet(() -> adoptLegacyDefaultWorkspaceIfNeeded(currentUserId)
                        .orElseGet(() -> createDefaultWorkspaceSafely(currentUserId)));
    }

    private String normalizeWorkspaceName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new InvalidWorkspaceNameException("Workspace name is required");
        }

        String normalizedName = name.trim();
        if (normalizedName.length() > MAX_WORKSPACE_NAME_LENGTH) {
            throw new InvalidWorkspaceNameException(
                    "Workspace name must be at most " + MAX_WORKSPACE_NAME_LENGTH + " characters"
            );
        }

        return normalizedName;
    }

    private Sort workspaceListSort() {
        return Sort.by(
                Sort.Order.asc("createdAt"),
                Sort.Order.asc("name")
        );
    }

    // Transitional legacy-adoption path for the configured local/dev default workspace only.
    private Optional<Workspace> adoptLegacyDefaultWorkspaceIfNeeded(String currentUserId) {
        if (!currentUserService.isDefaultUser(currentUserId)) {
            return Optional.empty();
        }

        return workspaceRepository.findById(workspaceProperties.getDefaultId())
                .map(workspace -> {
                    if (StringUtils.hasText(workspace.getOwnerId())
                            && !currentUserId.equals(workspace.getOwnerId())) {
                        throw new DefaultWorkspaceConflictException(
                                "DEFAULT_WORKSPACE_ID_CONFLICT",
                                "Configured default workspace ID is already owned by another user and cannot be adopted safely"
                        );
                    }

                    boolean changed = false;

                    if (!currentUserId.equals(workspace.getOwnerId())) {
                        workspace.setOwnerId(currentUserId);
                        changed = true;
                    }

                    if (!workspace.isDefaultWorkspace()) {
                        workspace.setDefaultWorkspace(true);
                        changed = true;
                    }

                    if (!StringUtils.hasText(workspace.getName())) {
                        workspace.setName(workspaceProperties.getDefaultName());
                        changed = true;
                    }

                    return changed ? workspaceRepository.save(workspace) : workspace;
                });
    }

    private Optional<Workspace> findOwnedDefaultWorkspace(String currentUserId) {
        List<Workspace> defaultWorkspaces = workspaceRepository.findAllByOwnerIdAndDefaultWorkspaceTrue(currentUserId);
        if (defaultWorkspaces.isEmpty()) {
            return Optional.empty();
        }
        if (defaultWorkspaces.size() > 1) {
            throw new DefaultWorkspaceConflictException(
                    "DEFAULT_WORKSPACE_CONFLICT",
                    "Multiple default workspaces exist for the current user"
            );
        }

        return Optional.of(defaultWorkspaces.get(0));
    }

    private Workspace createDefaultWorkspaceSafely(String currentUserId) {
        Workspace defaultWorkspace = new Workspace(
                defaultWorkspaceIdFor(currentUserId),
                workspaceProperties.getDefaultName(),
                currentUserId,
                true
        );

        try {
            return defaultWorkspaceCreationExecutor.create(defaultWorkspace);
        } catch (DataIntegrityViolationException exception) {
            return findOwnedDefaultWorkspace(currentUserId)
                    .orElseThrow(this::defaultWorkspaceIdConflict);
        }
    }

    private UUID defaultWorkspaceIdFor(String currentUserId) {
        if (currentUserService.isDefaultUser(currentUserId)) {
            return workspaceProperties.getDefaultId();
        }

        return UUID.nameUUIDFromBytes(("default-workspace:" + currentUserId).getBytes(StandardCharsets.UTF_8));
    }

    private DefaultWorkspaceConflictException defaultWorkspaceIdConflict() {
        return new DefaultWorkspaceConflictException(
                "DEFAULT_WORKSPACE_ID_CONFLICT",
                "Default workspace could not be created safely because the reserved workspace ID is already in use"
        );
    }
}
