package com.aiknowledgeworkspace.workspacecore.outbox.api;

public enum RelayExecutionPolicy {
    SCHEDULED_GLOBAL(true, false),
    SCHEDULED_SCOPED(false, false),
    EXPLICIT_OPERATOR(true, true);

    private final boolean requiresGlobalEnablement;
    private final boolean failOnIneligibleCandidate;

    RelayExecutionPolicy(boolean requiresGlobalEnablement, boolean failOnIneligibleCandidate) {
        this.requiresGlobalEnablement = requiresGlobalEnablement;
        this.failOnIneligibleCandidate = failOnIneligibleCandidate;
    }

    public boolean requiresGlobalEnablement() {
        return requiresGlobalEnablement;
    }

    public boolean failOnIneligibleCandidate() {
        return failOnIneligibleCandidate;
    }
}
