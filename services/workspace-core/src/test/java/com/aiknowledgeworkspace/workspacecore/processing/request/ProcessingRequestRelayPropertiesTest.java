package com.aiknowledgeworkspace.workspacecore.processing.request;

import com.aiknowledgeworkspace.workspacecore.processing.adapter.in.scheduling.ProcessingRequestRelayProperties;


import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ProcessingRequestRelayPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void defaultsToDisabledConservativeLocalSettings() {
        contextRunner.run(context -> {
            ProcessingRequestRelayProperties properties = context.getBean(ProcessingRequestRelayProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getFixedDelay()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.getBatchSize()).isEqualTo(10);
        });
    }

    @Test
    void invalidBatchSizeFailsClearly() {
        contextRunner
                .withPropertyValues("workspace.processing.request-relay.batch-size=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("workspace.processing.request-relay.batch-size")
                            .hasStackTraceContaining("at least 1");
                });
    }

    @Test
    void invalidFixedDelayFailsClearly() {
        contextRunner
                .withPropertyValues("workspace.processing.request-relay.fixed-delay=0s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("workspace.processing.request-relay.fixed-delay")
                            .hasStackTraceContaining("positive");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ProcessingRequestRelayProperties.class)
    static class TestConfiguration {
    }
}
