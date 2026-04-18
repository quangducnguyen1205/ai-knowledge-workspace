package com.aiknowledgeworkspace.workspacecore.workspace;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class TransactionalDefaultWorkspaceCreationExecutor implements DefaultWorkspaceCreationExecutor {

    private final WorkspaceRepository workspaceRepository;

    TransactionalDefaultWorkspaceCreationExecutor(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Workspace create(Workspace workspace) {
        return workspaceRepository.saveAndFlush(workspace);
    }
}
