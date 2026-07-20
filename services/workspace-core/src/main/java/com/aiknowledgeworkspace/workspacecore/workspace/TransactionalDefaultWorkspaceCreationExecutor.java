package com.aiknowledgeworkspace.workspacecore.workspace;

import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class TransactionalDefaultWorkspaceCreationExecutor implements DefaultWorkspaceCreationExecutor {

    private final WorkspaceStore workspaceStore;

    TransactionalDefaultWorkspaceCreationExecutor(WorkspaceStore workspaceStore) {
        this.workspaceStore = workspaceStore;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Workspace create(Workspace workspace) {
        return workspaceStore.saveAndFlush(workspace);
    }
}
