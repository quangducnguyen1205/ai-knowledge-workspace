package com.aiknowledgeworkspace.workspacecore.processing.application.service;

record ApplyProcessingResultCommand(
        ProcessingResultEventEnvelope event,
        String recoverableEventJson,
        boolean manualRecovery
) {
}
