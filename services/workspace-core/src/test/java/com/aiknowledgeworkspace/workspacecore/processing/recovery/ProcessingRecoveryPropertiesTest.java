package com.aiknowledgeworkspace.workspacecore.processing.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ProcessingRecoveryPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void invalidResultEventIdFailsClearly() {
        contextRunner
                .withPropertyValues("workspace.processing.recovery.result-event-id=not-a-uuid")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("workspace.processing.recovery.result-event-id")
                            .hasStackTraceContaining("not-a-uuid");
                });
    }

    @Test
    void invalidOutboxEventIdFailsClearly() {
        contextRunner
                .withPropertyValues("workspace.processing.recovery.outbox-event-id=not-a-uuid")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("workspace.processing.recovery.outbox-event-id")
                            .hasStackTraceContaining("not-a-uuid");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ProcessingRecoveryProperties.class)
    static class TestConfiguration {
    }
}
