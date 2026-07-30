package com.aiknowledgeworkspace.workspacecore.savedmoment.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.savedmoment.application.command.SaveMomentCommand;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.in.SavedMomentUseCase;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.result.SavedMomentListView;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.result.SavedMomentView;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/saved-moments")
public class SavedMomentController {

    private final SavedMomentUseCase savedMoments;

    public SavedMomentController(SavedMomentUseCase savedMoments) {
        this.savedMoments = savedMoments;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public SavedMomentResponse save(@RequestBody(required = false) SaveMomentRequest request) {
        return toResponse(savedMoments.save(new SaveMomentCommand(
                request == null ? null : request.assetId(),
                request == null ? null : request.transcriptRowId()
        )));
    }

    @GetMapping
    public SavedMomentListResponse list(
            @RequestParam(value = "workspaceId", required = false) UUID workspaceId
    ) {
        SavedMomentListView view = savedMoments.listForWorkspace(workspaceId);
        return new SavedMomentListResponse(
                view.workspaceId(),
                view.savedMomentCount(),
                view.maxItems(),
                view.items().stream().map(SavedMomentController::toResponse).toList()
        );
    }

    @DeleteMapping("/{savedMomentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID savedMomentId) {
        savedMoments.remove(savedMomentId);
    }

    private static SavedMomentResponse toResponse(SavedMomentView view) {
        return new SavedMomentResponse(
                view.savedMomentId(),
                view.workspaceId(),
                view.assetId(),
                view.assetTitle(),
                view.sourceType(),
                view.transcriptRowId(),
                view.segmentIndex(),
                view.startMs(),
                view.endMs(),
                view.text(),
                view.savedAt()
        );
    }
}
