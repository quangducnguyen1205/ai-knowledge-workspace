package com.aiknowledgeworkspace.workspacecore.workspace;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping
    public ResponseEntity<WorkspaceResponse> createWorkspace(@RequestBody CreateWorkspaceRequest request) {
        Workspace workspace = workspaceService.createWorkspace(request.name());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WorkspaceResponse.from(workspace));
    }

    @GetMapping
    public List<WorkspaceResponse> listWorkspaces() {
        return workspaceService.listWorkspaces().stream()
                .map(WorkspaceResponse::from)
                .toList();
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceResponse getWorkspace(@PathVariable UUID workspaceId) {
        return WorkspaceResponse.from(workspaceService.getWorkspace(workspaceId));
    }
}
