package com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class FastApiPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void defaultsToAssistantSpecificSeventyFiveSecondReadTimeout() {
        contextRunner.run(context -> {
            FastApiProperties properties = context.getBean(FastApiProperties.class);

            assertThat(properties.getAssistantReadTimeout()).isEqualTo(Duration.ofSeconds(75));
            assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(30));
        });
    }

    @Test
    void acceptsExplicitAssistantReadTimeoutEnvironmentOverride() {
        contextRunner
                .withSystemProperties("FASTAPI_ASSISTANT_READ_TIMEOUT=91s")
                .run(context -> assertThat(context.getBean(FastApiProperties.class).getAssistantReadTimeout())
                        .isEqualTo(Duration.ofSeconds(91)));
    }

    @Test
    void rejectsBaseUrlWithoutHttpSchemeOrHost() {
        contextRunner
                .withPropertyValues("integration.fastapi.base-url=localhost:8000")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("integration.fastapi.base-url")
                            .hasStackTraceContaining("plain HTTP or HTTPS URI with a host");
                });
    }

    @Test
    void rejectsAccidentallyQuotedOrEscapedBaseUrl() {
        for (String invalidBaseUrl : new String[]{"'http://127.0.0.1:8000'", "http:\\\\127.0.0.1:8000"}) {
            contextRunner
                    .withPropertyValues("integration.fastapi.base-url=" + invalidBaseUrl)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .hasStackTraceContaining("integration.fastapi.base-url")
                                .hasStackTraceContaining("shell quoting or escaping");
                    });
        }
    }

    @Test
    void rejectsNonPositiveAssistantReadTimeout() {
        contextRunner
                .withPropertyValues("integration.fastapi.assistant-read-timeout=0s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("integration.fastapi.assistant-read-timeout")
                            .hasStackTraceContaining("must be positive");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FastApiProperties.class)
    static class TestConfiguration {
    }
}
