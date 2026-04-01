package com.aiknowledgeworkspace.workspacecore.workspace;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceService {

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
    public Workspace resolveWorkspace(UUID workspaceId) {
        if (workspaceId == null || workspaceProperties.getDefaultId().equals(workspaceId)) {
            return ensureDefaultWorkspace();
        }

        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
    }

    @Transactional
    public Workspace ensureDefaultWorkspace() {
        return workspaceRepository.findById(workspaceProperties.getDefaultId())
                .orElseGet(() -> workspaceRepository.save(
                        new Workspace(workspaceProperties.getDefaultId(), workspaceProperties.getDefaultName())
                ));
    }
}
