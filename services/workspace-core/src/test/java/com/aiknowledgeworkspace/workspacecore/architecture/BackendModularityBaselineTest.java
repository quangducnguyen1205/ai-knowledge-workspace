package com.aiknowledgeworkspace.workspacecore.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.WorkspaceCoreApplication;
import java.util.List;
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

    @Test
    void defaultDirectPackageDetectionFindsCurrentModuleRoots() {
        ApplicationModules modules = ApplicationModules.of(WorkspaceCoreApplication.class);

        assertThat(detectedModuleNames(modules)).containsExactlyElementsOf(EXPECTED_MODULE_NAMES);
    }

    @Test
    void strictApplicationModulesVerificationPasses() {
        ApplicationModules.of(WorkspaceCoreApplication.class).verify();
    }

    private static List<String> detectedModuleNames(ApplicationModules modules) {
        return modules.stream()
                .map(module -> module.getName())
                .sorted()
                .toList();
    }

}
