package com.aiknowledgeworkspace.workspacecore.workspace;

import com.aiknowledgeworkspace.workspacecore.common.identity.CurrentUserService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WorkspaceService {

    private static final int MAX_WORKSPACE_NAME_LENGTH = 255;

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceProperties workspaceProperties;
    private final CurrentUserService currentUserService;

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            WorkspaceProperties workspaceProperties,
            CurrentUserService currentUserService
    ) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceProperties = workspaceProperties;
        this.currentUserService = currentUserService;
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
        return workspaceRepository.findByOwnerIdAndDefaultWorkspaceTrue(currentUserId)
                .orElseGet(() -> adoptLegacyDefaultWorkspaceIfNeeded(currentUserId)
                        .orElseGet(() -> createDefaultWorkspace(currentUserId)));
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

    private java.util.Optional<Workspace> adoptLegacyDefaultWorkspaceIfNeeded(String currentUserId) {
        if (!currentUserService.isDefaultUser(currentUserId)) {
            return java.util.Optional.empty();
        }

        return workspaceRepository.findById(workspaceProperties.getDefaultId())
                .filter(workspace -> !StringUtils.hasText(workspace.getOwnerId())
                        || currentUserId.equals(workspace.getOwnerId()))
                .map(workspace -> {
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

    private Workspace createDefaultWorkspace(String currentUserId) {
        UUID workspaceId = currentUserService.isDefaultUser(currentUserId)
                ? workspaceProperties.getDefaultId()
                : null;

        return workspaceRepository.save(new Workspace(
                workspaceId,
                workspaceProperties.getDefaultName(),
                currentUserId,
                true
        ));
    }
}
