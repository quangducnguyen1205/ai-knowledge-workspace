package com.aiknowledgeworkspace.workspacecore.workspace.application.internal;

import com.aiknowledgeworkspace.workspacecore.workspace.DefaultWorkspaceConflictException;
import com.aiknowledgeworkspace.workspacecore.workspace.DefaultWorkspaceCreationExecutor;
import com.aiknowledgeworkspace.workspacecore.workspace.InvalidWorkspaceNameException;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceDeleteConflictException;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceNotFoundException;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceProperties;

import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceUseCase;
import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceView;
import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceAccess;
import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceAccessApplication;
import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceQueryApplication;
import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;

import com.aiknowledgeworkspace.workspacecore.common.identity.api.CurrentUserContext;
import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceAssetUsagePort;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WorkspaceService implements WorkspaceUseCase, WorkspaceAccessApplication, WorkspaceQueryApplication {

    private static final int MAX_WORKSPACE_NAME_LENGTH = 255;

    private final WorkspaceStore workspaceStore;
    private final WorkspaceAssetUsagePort workspaceAssetUsagePort;
    private final WorkspaceProperties workspaceProperties;
    private final CurrentUserContext currentUserService;
    private final DefaultWorkspaceCreationExecutor defaultWorkspaceCreationExecutor;
    private final WorkspaceAccessPolicy workspaceAccessPolicy;

    @Autowired
    public WorkspaceService(
            WorkspaceStore workspaceStore,
            WorkspaceAssetUsagePort workspaceAssetUsagePort,
            WorkspaceProperties workspaceProperties,
            CurrentUserContext currentUserService,
            DefaultWorkspaceCreationExecutor defaultWorkspaceCreationExecutor,
            WorkspaceAccessPolicy workspaceAccessPolicy
    ) {
        this.workspaceStore = workspaceStore;
        this.workspaceAssetUsagePort = workspaceAssetUsagePort;
        this.workspaceProperties = workspaceProperties;
        this.currentUserService = currentUserService;
        this.defaultWorkspaceCreationExecutor = defaultWorkspaceCreationExecutor;
        this.workspaceAccessPolicy = workspaceAccessPolicy;
    }

    public WorkspaceService(
            WorkspaceStore workspaceStore,
            WorkspaceAssetUsagePort workspaceAssetUsagePort,
            WorkspaceProperties workspaceProperties,
            CurrentUserContext currentUserService
    ) {
        this(
                workspaceStore,
                workspaceAssetUsagePort,
                workspaceProperties,
                currentUserService,
                workspaceStore::save,
                new WorkspaceAccessPolicy(currentUserService)
        );
    }

    public WorkspaceService(
            WorkspaceStore workspaceStore,
            WorkspaceAssetUsagePort workspaceAssetUsagePort,
            WorkspaceProperties workspaceProperties,
            CurrentUserContext currentUserService,
            DefaultWorkspaceCreationExecutor defaultWorkspaceCreationExecutor
    ) {
        this(
                workspaceStore,
                workspaceAssetUsagePort,
                workspaceProperties,
                currentUserService,
                defaultWorkspaceCreationExecutor,
                new WorkspaceAccessPolicy(currentUserService)
        );
    }

    @Transactional
    public Workspace createWorkspace(String name) {
        return workspaceStore.save(new Workspace(
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
        return workspaceStore.findOwned(currentUserId);
    }

    @Transactional
    public Workspace getWorkspace(UUID workspaceId) {
        return resolveOwnedWorkspaceOrDefault(workspaceId);
    }

    @Transactional
    public Workspace updateWorkspace(UUID workspaceId, String name) {
        String normalizedName = normalizeWorkspaceName(name);
        Workspace workspace = resolveOwnedWorkspaceOrDefault(workspaceId);
        workspace.setName(normalizedName);
        return workspaceStore.save(workspace);
    }

    @Transactional
    public void deleteWorkspace(UUID workspaceId) {
        Workspace workspace = resolveOwnedWorkspaceOrDefault(workspaceId);

        if (workspace.isDefaultWorkspace()) {
            throw new WorkspaceDeleteConflictException(
                    "DEFAULT_WORKSPACE_DELETE_FORBIDDEN",
                    "Default workspace cannot be deleted"
            );
        }

        if (workspaceAssetUsagePort.workspaceHasAssets(workspace.getId())) {
            throw new WorkspaceDeleteConflictException(
                    "WORKSPACE_NOT_EMPTY",
                    "Workspace cannot be deleted while it still contains assets"
            );
        }

        workspaceStore.delete(workspace);
    }

    public boolean isDefaultWorkspace(Workspace workspace) {
        return workspace != null && workspace.isDefaultWorkspace();
    }

    public boolean isOwnedByCurrentUser(Workspace workspace) {
        return workspaceAccessPolicy.isOwnedByCurrentUser(workspace);
    }

    public boolean isOwnedByCurrentUser(UUID workspaceId) {
        if (workspaceId == null) {
            return false;
        }
        String currentUserId = currentUserService.getCurrentUserId();
        return workspaceStore.findOwnedById(workspaceId, currentUserId).isPresent();
    }

    @Transactional
    public Workspace resolveOwnedWorkspaceOrDefault(UUID workspaceId) {
        String currentUserId = currentUserService.getCurrentUserId();
        if (workspaceId == null) {
            return ensureDefaultWorkspace(currentUserId);
        }

        return workspaceStore.findOwnedById(workspaceId, currentUserId)
                .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
    }

    public UUID resolveWorkspaceId(UUID requestedWorkspaceId) {
        return resolveOwnedWorkspaceOrDefault(requestedWorkspaceId).getId();
    }

    @Override
    public WorkspaceAccess resolveWorkspaceOrDefault(UUID requestedWorkspaceId) {
        Workspace workspace = resolveOwnedWorkspaceOrDefault(requestedWorkspaceId);
        return new WorkspaceAccess(workspace.getId(), workspace.getOwnerId());
    }

    @Transactional
    public Workspace ensureDefaultWorkspace() {
        return ensureDefaultWorkspace(currentUserService.getCurrentUserId());
    }

    @Transactional
    public Workspace ensureDefaultWorkspace(String currentUserId) {
        return findOwnedDefaultWorkspace(currentUserId)
                .orElseGet(() -> createDefaultWorkspaceSafely(currentUserId));
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

    private Optional<Workspace> findOwnedDefaultWorkspace(String currentUserId) {
        List<Workspace> defaultWorkspaces = workspaceStore.findOwnedDefaults(currentUserId);
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

    @Override
    public WorkspaceView create(String name) {
        return WorkspaceView.from(createWorkspace(name));
    }

    @Override
    public List<WorkspaceView> list() {
        return listWorkspaces().stream().map(WorkspaceView::from).toList();
    }

    @Override
    public WorkspaceView get(UUID workspaceId) {
        return WorkspaceView.from(getWorkspace(workspaceId));
    }

    @Override
    public WorkspaceView update(UUID workspaceId, String name) {
        return WorkspaceView.from(updateWorkspace(workspaceId, name));
    }

    @Override
    public void delete(UUID workspaceId) {
        deleteWorkspace(workspaceId);
    }
}
