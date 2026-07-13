package com.aiknowledgeworkspace.workspacecore.processing.result;

record ApplyProcessingResultCommand(
        ProcessingResultEventEnvelope event,
        String recoverableEventJson,
        boolean manualRecovery
) {
}
