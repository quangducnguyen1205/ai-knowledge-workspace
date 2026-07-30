package com.aiknowledgeworkspace.workspacecore.common.web.api;

/**
 * Bounded runtime identity of the deployed revision.
 *
 * <p>Deliberately narrow: application name, version, git commit and build time only. It carries no
 * repository path, no environment variable, no credential, no username and no dependency list, so
 * it is safe to expose to an authenticated operator without becoming an information-disclosure
 * surface. Any field the build did not supply degrades to {@code null} rather than to a guess.
 */
public record BuildIdentity(
        String application,
        String version,
        String gitCommit,
        String buildTime
) {
}
