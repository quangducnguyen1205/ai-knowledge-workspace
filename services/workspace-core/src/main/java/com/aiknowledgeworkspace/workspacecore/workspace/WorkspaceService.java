package com.aiknowledgeworkspace.workspacecore.workspace;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
