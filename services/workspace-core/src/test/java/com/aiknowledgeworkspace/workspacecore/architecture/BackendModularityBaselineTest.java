package com.aiknowledgeworkspace.workspacecore.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.WorkspaceCoreApplication;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;

class BackendModularityBaselineTest {

    private static final List<String> EXPECTED_MODULE_NAMES = List.of(
            "asset",
            "assistant",
            "common",
            "integration",
            "outbox",
            "processing",
            "search",
            "storage",
            "workspace");

    private static final String BASELINE_RESOURCE =
            "architecture/spring-modulith-violations-baseline.txt";

    @Test
    void defaultDirectPackageDetectionFindsCurrentModuleRoots() {
        ApplicationModules modules = ApplicationModules.of(WorkspaceCoreApplication.class);

        assertThat(detectedModuleNames(modules)).containsExactlyElementsOf(EXPECTED_MODULE_NAMES);
    }

    @Test
    void currentModuleViolationsMatchCommittedBaseline() throws IOException {
        ApplicationModules modules = ApplicationModules.of(WorkspaceCoreApplication.class);

        String actual = baselineReport(modules);
        String expected = normalize(new ClassPathResource(BASELINE_RESOURCE)
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(actual)
                .as("Spring Modulith violation baseline changed. Update this baseline only after "
                        + "intentional architectural review; strict ApplicationModules.verify() "
                        + "is expected to remain red until the known module cycles are removed.")
                .isEqualTo(expected);
    }

    private static List<String> detectedModuleNames(ApplicationModules modules) {
        return modules.stream()
                .map(module -> module.getName())
                .sorted()
                .toList();
    }

    private static String baselineReport(ApplicationModules modules) {
        Violations violations = modules.detectViolations();
        List<String> violationMessages = violations.getMessages().stream()
                .map(BackendModularityBaselineTest::normalize)
                .sorted()
                .toList();

        return normalize("""
                # Spring Modulith violation baseline
                # Generated from ApplicationModules.of(WorkspaceCoreApplication.class).detectViolations().
                # This is a ratchet, not proof of a clean modular architecture.
                # Do not regenerate automatically; update only after intentional architecture review.
                detected-modules=%s
                violation-message-count=%d

                %s
                """.formatted(
                String.join(",", detectedModuleNames(modules)),
                violationMessages.size(),
                violationMessages.stream()
                        .map(message -> "---\n" + message)
                        .collect(Collectors.joining("\n"))));
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .lines()
                .map(String::stripTrailing)
                .collect(Collectors.joining("\n", "", "\n"));
    }
}
