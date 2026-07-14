package com.aiknowledgeworkspace.workspacecore.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.WorkspaceCoreApplication;
import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

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

    private static final Pattern JAVA_SOURCE_LOCATION =
            Pattern.compile("\\(([^()\\n]+\\.java):\\d+\\)");

    @Test
    void defaultDirectPackageDetectionFindsCurrentModuleRoots() {
        ApplicationModules modules = ApplicationModules.of(WorkspaceCoreApplication.class);

        assertThat(detectedModuleNames(modules)).containsExactlyElementsOf(EXPECTED_MODULE_NAMES);
    }

    @Test
    void strictApplicationModulesVerificationPasses() {
        ApplicationModules.of(WorkspaceCoreApplication.class).verify();
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
