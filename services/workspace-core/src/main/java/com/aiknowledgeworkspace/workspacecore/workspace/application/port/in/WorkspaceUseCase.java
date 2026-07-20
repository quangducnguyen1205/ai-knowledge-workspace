package com.aiknowledgeworkspace.workspacecore.workspace.application.port.in;

import com.aiknowledgeworkspace.workspacecore.workspace.application.result.WorkspaceView;

import java.util.List;
import java.util.UUID;

public interface WorkspaceUseCase {

    WorkspaceView create(String name);

    List<WorkspaceView> list();

    WorkspaceView get(UUID workspaceId);

    WorkspaceView update(UUID workspaceId, String name);

    void delete(UUID workspaceId);
}
