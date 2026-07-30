package com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.in;

import com.aiknowledgeworkspace.workspacecore.savedmoment.application.command.SaveMomentCommand;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.result.SavedMomentListView;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.result.SavedMomentView;
import java.util.UUID;

public interface SavedMomentUseCase {

    SavedMomentView save(SaveMomentCommand command);

    SavedMomentListView listForWorkspace(UUID requestedWorkspaceId);

    void remove(UUID savedMomentId);
}
