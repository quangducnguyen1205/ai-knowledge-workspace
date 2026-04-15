package com.aiknowledgeworkspace.workspacecore.workspace;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    List<Workspace> findByOwnerId(String ownerId, Sort sort);

    Optional<Workspace> findByIdAndOwnerId(UUID id, String ownerId);

    Optional<Workspace> findByOwnerIdAndDefaultWorkspaceTrue(String ownerId);
}
