package com.aiknowledgeworkspace.workspacecore.common.web.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.common.web.api.BuildIdentity;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports which revision is running.
 *
 * <p>{@link BuildProperties} exists only when the build generated
 * {@code META-INF/build-info.properties}. When it is absent — a plain {@code mvn test} run or an
 * IDE launch — the endpoint still answers with the application name and null build fields instead
 * of failing, so a deployment can be probed the same way in every environment.
 */
@RestController
@RequestMapping("/api/build-info")
public class BuildIdentityController {

    private static final String UNKNOWN_COMMIT = "unknown";

    private final String applicationName;
    private final BuildProperties buildProperties;

    public BuildIdentityController(
            @Value("${spring.application.name:workspace-core}") String applicationName,
            ObjectProvider<BuildProperties> buildProperties
    ) {
        this.applicationName = applicationName;
        this.buildProperties = buildProperties.getIfAvailable();
    }

    @GetMapping
    public BuildIdentity buildInfo() {
        if (buildProperties == null) {
            return new BuildIdentity(applicationName, null, null, null);
        }
        return new BuildIdentity(
                applicationName,
                textOrNull(buildProperties.getVersion()),
                commitOrNull(buildProperties.get("commit")),
                instantOrNull(buildProperties.getTime())
        );
    }

    private static String textOrNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** A commit the build could not resolve is reported as absent rather than as a placeholder string. */
    private static String commitOrNull(String commit) {
        String normalized = textOrNull(commit);
        return normalized == null || UNKNOWN_COMMIT.equalsIgnoreCase(normalized) ? null : normalized;
    }

    private static String instantOrNull(Instant time) {
        return time == null ? null : time.toString();
    }
}
