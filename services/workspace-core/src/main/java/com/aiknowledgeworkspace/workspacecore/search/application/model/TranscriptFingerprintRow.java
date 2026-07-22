package com.aiknowledgeworkspace.workspacecore.search.application.model;

public interface TranscriptFingerprintRow {
    Integer segmentIndex();

    Long startMs();

    Long endMs();

    String text();
}
