package com.aiknowledgeworkspace.workspacecore;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.configuration.FastApiProperties;
import com.aiknowledgeworkspace.workspacecore.identity.application.configuration.WorkspaceSecurityProperties;
import com.aiknowledgeworkspace.workspacecore.outbox.api.WorkspaceKafkaProperties;
import com.aiknowledgeworkspace.workspacecore.outbox.application.configuration.OutboxRecoveryProperties;
import com.aiknowledgeworkspace.workspacecore.processing.adapter.in.scheduling.ProcessingRequestRelayProperties;
import com.aiknowledgeworkspace.workspacecore.search.application.configuration.SearchIndexingProperties;
import com.aiknowledgeworkspace.workspacecore.search.adapter.in.scheduling.IndexingRequestRelayProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class CoherentAsyncProductProfileValidatorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void project3ProfileLoadsTheCompleteAsynchronousProductConfiguration() {
        contextRunner
                .withPropertyValues("spring.profiles.active=project3")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    ProcessingRequestRelayProperties processingRelay = context.getBean(
                            ProcessingRequestRelayProperties.class
                    );
                    SearchIndexingProperties indexing = context.getBean(SearchIndexingProperties.class);
                    IndexingRequestRelayProperties indexingRelay = context.getBean(IndexingRequestRelayProperties.class);
                    WorkspaceKafkaProperties kafka = context.getBean(WorkspaceKafkaProperties.class);
                    WorkspaceSecurityProperties security = context.getBean(WorkspaceSecurityProperties.class);
                    FastApiProperties fastApi = context.getBean(FastApiProperties.class);
                    OutboxRecoveryProperties recovery = context.getBean(OutboxRecoveryProperties.class);

                    assertThat(processingRelay.isEnabled()).isTrue();
                    assertThat(indexing.isAutoRequestEnabled()).isTrue();
                    assertThat(indexingRelay.isEnabled()).isTrue();
                    assertThat(kafka.isEnabled()).isTrue();
                    assertThat(kafka.isProcessingResultListenerEnabled()).isTrue();
                    assertThat(kafka.isIndexingListenerEnabled()).isTrue();
                    assertThat(security.isLegacySessionMode()).isTrue();
                    assertThat(fastApi.getBaseUrl()).isEqualTo("http://127.0.0.1:8000");
                    assertThat(fastApi.getAssistantReadTimeout()).isEqualTo(Duration.ofSeconds(75));
                    assertThat(recovery.isEnabled()).isTrue();
                });
    }

    @Test
    void incompleteKafkaRequestConfigurationFailsFastWithSafePropertyNames() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=project3",
                        "workspace.kafka.processing-result-listener-enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("Incomplete project3 asynchronous configuration")
                            .hasStackTraceContaining("workspace.kafka.processing-result-listener-enabled");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            ProcessingRequestRelayProperties.class,
            SearchIndexingProperties.class,
            IndexingRequestRelayProperties.class,
            WorkspaceKafkaProperties.class,
            WorkspaceSecurityProperties.class,
            OutboxRecoveryProperties.class,
            FastApiProperties.class,
    })
    @Import(CoherentAsyncProductProfileValidator.class)
    static class TestConfiguration {
    }
}
