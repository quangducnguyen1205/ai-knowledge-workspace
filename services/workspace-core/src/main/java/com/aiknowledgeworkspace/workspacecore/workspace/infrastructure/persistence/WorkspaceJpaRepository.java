package com.aiknowledgeworkspace.workspacecore.workspace.infrastructure.persistence;

import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WorkspaceJpaRepository extends JpaRepository<Workspace, UUID> {

    List<Workspace> findByOwnerIdOrderByCreatedAtAscNameAsc(String ownerId);

    Optional<Workspace> findByIdAndOwnerId(UUID id, String ownerId);

    List<Workspace> findAllByOwnerIdAndDefaultWorkspaceTrue(String ownerId);
}
