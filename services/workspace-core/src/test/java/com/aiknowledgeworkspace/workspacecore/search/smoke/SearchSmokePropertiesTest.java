package com.aiknowledgeworkspace.workspacecore.search.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SearchSmokePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void invalidIndexingOutboxEventIdFailsClearly() {
        contextRunner
                .withPropertyValues("workspace.search.smoke.indexing-outbox-event-id=not-a-uuid")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("workspace.search.smoke.indexing-outbox-event-id")
                            .hasStackTraceContaining("not-a-uuid");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SearchSmokeProperties.class)
    static class TestConfiguration {
    }
}
