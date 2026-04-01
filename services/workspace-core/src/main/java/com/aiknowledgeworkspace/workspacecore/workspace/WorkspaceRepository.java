package com.aiknowledgeworkspace.workspacecore.workspace;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
}
