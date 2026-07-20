package com.aiknowledgeworkspace.workspacecore.workspace.application.port.out;

import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceStore {

    Workspace save(Workspace workspace);

    Workspace saveAndFlush(Workspace workspace);

    void delete(Workspace workspace);

    List<Workspace> findOwned(String ownerId);

    Optional<Workspace> findOwnedById(UUID workspaceId, String ownerId);

    List<Workspace> findOwnedDefaults(String ownerId);
}
