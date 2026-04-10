package com.aiknowledgeworkspace.workspacecore.workspace;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceService {

    private static final int MAX_WORKSPACE_NAME_LENGTH = 255;

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceProperties workspaceProperties;

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            WorkspaceProperties workspaceProperties
    ) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceProperties = workspaceProperties;
    }

    @Transactional
    public Workspace createWorkspace(String name) {
        return workspaceRepository.save(new Workspace(null, normalizeWorkspaceName(name)));
    }

    @Transactional
    public List<Workspace> listWorkspaces() {
        ensureDefaultWorkspace();
        return workspaceRepository.findAll(Sort.by(
                Sort.Order.asc("createdAt"),
                Sort.Order.asc("name")
        ));
    }

    @Transactional
    public Workspace getWorkspace(UUID workspaceId) {
        return resolveWorkspaceOrDefault(workspaceId);
    }

    public UUID getDefaultWorkspaceId() {
        return workspaceProperties.getDefaultId();
    }

    @Transactional
    public Workspace resolveWorkspaceOrDefault(UUID workspaceId) {
        if (workspaceId == null || workspaceProperties.getDefaultId().equals(workspaceId)) {
            return ensureDefaultWorkspace();
        }

        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
    }

    @Transactional
    public Workspace ensureDefaultWorkspace() {
        return workspaceRepository.findById(workspaceProperties.getDefaultId())
                .orElseGet(() -> workspaceRepository.save(
                        new Workspace(workspaceProperties.getDefaultId(), workspaceProperties.getDefaultName())
                ));
    }

    private String normalizeWorkspaceName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workspace name is required");
        }

        String normalizedName = name.trim();
        if (normalizedName.length() > MAX_WORKSPACE_NAME_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Workspace name must be at most " + MAX_WORKSPACE_NAME_LENGTH + " characters"
            );
        }

        return normalizedName;
    }
}
