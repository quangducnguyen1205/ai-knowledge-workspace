package com.aiknowledgeworkspace.workspacecore.search.application.configuration;

/** What the operator asked the process to do on startup. Nothing, unless explicitly set. */
public enum SearchRebuildCommand {

    NONE,

    /** Report how many assets a rebuild would touch, writing nothing. */
    REPORT_CANDIDATES,

    /** Rebuild the whole Elasticsearch projection from canonical PostgreSQL state. */
    REBUILD_ALL
}
