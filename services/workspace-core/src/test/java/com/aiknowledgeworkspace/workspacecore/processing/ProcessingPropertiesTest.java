package com.aiknowledgeworkspace.workspacecore.processing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ProcessingPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void defaultsToDirectUpload() {
        contextRunner.run(context -> assertThat(context.getBean(ProcessingProperties.class).getTriggerMode())
                .isEqualTo(ProcessingTriggerMode.DIRECT_UPLOAD));
    }

    @Test
    void acceptsExplicitKafkaRequestMode() {
        contextRunner
                .withPropertyValues("workspace.processing.trigger-mode=kafka_request")
                .run(context -> assertThat(context.getBean(ProcessingProperties.class).getTriggerMode())
                        .isEqualTo(ProcessingTriggerMode.KAFKA_REQUEST));
    }

    @Test
    void invalidTriggerModeFailsClearly() {
        contextRunner
                .withPropertyValues("workspace.processing.trigger-mode=not_a_mode")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("workspace.processing.trigger-mode")
                            .hasStackTraceContaining("not_a_mode");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ProcessingProperties.class)
    static class TestConfiguration {
    }
}
