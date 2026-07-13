package com.aiknowledgeworkspace.workspacecore.search;

public class SearchProcessingJobNotFoundException extends RuntimeException {
    public SearchProcessingJobNotFoundException() {
        super("Processing job not found");
    }
}
