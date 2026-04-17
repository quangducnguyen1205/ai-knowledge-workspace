package com.aiknowledgeworkspace.workspacecore.asset;

public class ProcessingJobNotFoundException extends RuntimeException {

    public ProcessingJobNotFoundException() {
        super("Processing job not found");
    }
}
