package com.aiknowledgeworkspace.workspacecore.asset.application.exception;

public class ProcessingJobNotFoundException extends RuntimeException {

    public ProcessingJobNotFoundException() {
        super("Processing job not found");
    }
}
