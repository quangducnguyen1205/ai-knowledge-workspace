package com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset;

public class SearchCanonicalContextLoadException extends RuntimeException {

    public SearchCanonicalContextLoadException() {
        super("Canonical search context is unavailable");
    }
}
