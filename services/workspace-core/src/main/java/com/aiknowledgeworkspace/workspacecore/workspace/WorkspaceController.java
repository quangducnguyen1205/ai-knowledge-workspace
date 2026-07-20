package com.aiknowledgeworkspace.workspacecore.workspace;

import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceUseCase;
import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceView;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceUseCase workspaceUseCase;

    public WorkspaceController(WorkspaceUseCase workspaceUseCase) {
        this.workspaceUseCase = workspaceUseCase;
    }

    @PostMapping
    public ResponseEntity<WorkspaceResponse> createWorkspace(@RequestBody(required = false) CreateWorkspaceRequest request) {
        WorkspaceView workspace = workspaceUseCase.create(request == null ? null : request.name());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WorkspaceResponse.from(workspace));
    }

    @GetMapping
    public List<WorkspaceResponse> listWorkspaces() {
        return workspaceUseCase.list().stream()
                .map(WorkspaceResponse::from)
                .toList();
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceResponse getWorkspace(@PathVariable UUID workspaceId) {
        return WorkspaceResponse.from(workspaceUseCase.get(workspaceId));
    }

    @PatchMapping("/{workspaceId}")
    public WorkspaceResponse updateWorkspace(
            @PathVariable UUID workspaceId,
            @RequestBody(required = false) UpdateWorkspaceRequest request
    ) {
        return WorkspaceResponse.from(workspaceUseCase.update(
                workspaceId,
                request == null ? null : request.name()
        ));
    }

    @DeleteMapping("/{workspaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWorkspace(@PathVariable UUID workspaceId) {
        workspaceUseCase.delete(workspaceId);
    }
}
