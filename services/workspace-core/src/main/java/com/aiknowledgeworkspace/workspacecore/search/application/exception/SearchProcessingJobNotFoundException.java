package com.aiknowledgeworkspace.workspacecore.search.application.exception;

public class SearchProcessingJobNotFoundException extends RuntimeException {
    public SearchProcessingJobNotFoundException() {
        super("Processing job not found");
    }
}
