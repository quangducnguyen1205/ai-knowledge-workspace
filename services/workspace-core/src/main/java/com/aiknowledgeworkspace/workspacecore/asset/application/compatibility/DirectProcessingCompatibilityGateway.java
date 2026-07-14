package com.aiknowledgeworkspace.workspacecore.asset.application.compatibility;

import java.util.List;

/** Neutral asset-owned boundary for the retained direct-processing compatibility path. */
public interface DirectProcessingCompatibilityGateway {

    DirectProcessingUploadResult upload(DirectProcessingUploadCommand command);

    DirectProcessingTaskState taskState(String taskId);

    List<DirectProcessingTranscriptRow> transcriptRows(String videoId);
}
