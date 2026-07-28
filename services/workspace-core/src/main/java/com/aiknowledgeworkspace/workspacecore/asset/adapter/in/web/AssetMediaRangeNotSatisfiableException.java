package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

class AssetMediaRangeNotSatisfiableException extends RuntimeException {

    private final long totalSizeBytes;

    AssetMediaRangeNotSatisfiableException(long totalSizeBytes) {
        super("Requested media range is not satisfiable");
        this.totalSizeBytes = Math.max(0, totalSizeBytes);
    }

    long getTotalSizeBytes() {
        return totalSizeBytes;
    }
}
