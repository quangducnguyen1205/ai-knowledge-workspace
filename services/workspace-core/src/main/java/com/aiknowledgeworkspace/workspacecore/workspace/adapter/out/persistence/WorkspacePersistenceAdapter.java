package com.aiknowledgeworkspace.workspacecore.workspace.adapter.out.persistence;

import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class WorkspacePersistenceAdapter implements WorkspaceStore {

    private final WorkspaceJpaRepository repository;

    WorkspacePersistenceAdapter(WorkspaceJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Workspace save(Workspace workspace) {
        return repository.save(workspace);
    }

    @Override
    public Workspace saveAndFlush(Workspace workspace) {
        return repository.saveAndFlush(workspace);
    }

    @Override
    public void delete(Workspace workspace) {
        repository.delete(workspace);
    }

    @Override
    public List<Workspace> findOwned(String ownerId) {
        return repository.findByOwnerIdOrderByCreatedAtAscNameAsc(ownerId);
    }

    @Override
    public Optional<Workspace> findOwnedById(UUID workspaceId, String ownerId) {
        return repository.findByIdAndOwnerId(workspaceId, ownerId);
    }

    @Override
    public List<Workspace> findOwnedDefaults(String ownerId) {
        return repository.findAllByOwnerIdAndDefaultWorkspaceTrue(ownerId);
    }
}
