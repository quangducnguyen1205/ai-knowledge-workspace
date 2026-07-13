package com.aiknowledgeworkspace.workspacecore.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.WorkspaceCoreApplication;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.HexFormat;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
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

    private static final Pattern JAVA_SOURCE_LOCATION =
            Pattern.compile("\\(([^()\\n]+\\.java):\\d+\\)");
    private static final Pattern CYCLE_SLICE = Pattern.compile("Slice ([a-z]+)");

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

    @Test
    void normalizationRemovesOnlyJavaSourceLineLocations() {
        String input = "Method calls dependency in (AssetPersistenceService.java:104)\r\n"
                + "Constructor declared in (ProcessingResultEventHandler.java:0)\r"
                + "Business count 137 and version 1.2.5 stay visible\n";

        assertThat(normalize(input)).isEqualTo("""
                Method calls dependency in (AssetPersistenceService.java)
                Constructor declared in (ProcessingResultEventHandler.java)
                Business count 137 and version 1.2.5 stay visible
                """);
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
        List<String> cycles = violationMessages.stream()
                .filter(message -> message.startsWith("Cycle detected:"))
                .map(BackendModularityBaselineTest::cyclePath)
                .sorted()
                .toList();

        String cycleLines = cycles.stream()
                .map(cycle -> "cycle=" + cycle)
                .collect(Collectors.joining("\n"));
        String header = """
                # Spring Modulith violation baseline
                # The aggregate SHA-256 fingerprints the exact sorted normalized violation set.
                # Human-readable cycle paths remain listed for dependency review.
                # This is a strict ratchet, not proof of a clean modular architecture.
                detected-modules=%s
                violation-message-count=%d
                cycle-message-count=%d
                violation-set-sha256=%s
                """.formatted(
                String.join(",", detectedModuleNames(modules)),
                violationMessages.size(),
                cycles.size(),
                sha256(String.join("\n---\n", violationMessages)));
        return normalize(cycleLines.isEmpty() ? header : header + "\n" + cycleLines + "\n");
    }

    private static String cyclePath(String violationMessage) {
        String cycleHeader = violationMessage.split("\\n  1\\.", 2)[0];
        return CYCLE_SLICE.matcher(cycleHeader).results()
                .map(match -> match.group(1))
                .collect(Collectors.joining(" -> "));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String normalize(String value) {
        return JAVA_SOURCE_LOCATION.matcher(value)
                .replaceAll("($1)")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .lines()
                .map(String::stripTrailing)
                .collect(Collectors.joining("\n", "", "\n"));
    }
}
