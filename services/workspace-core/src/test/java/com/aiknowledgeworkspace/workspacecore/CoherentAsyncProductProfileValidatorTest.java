package com.aiknowledgeworkspace.workspacecore;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.common.config.FastApiProperties;
import com.aiknowledgeworkspace.workspacecore.common.identity.WorkspaceSecurityProperties;
import com.aiknowledgeworkspace.workspacecore.outbox.WorkspaceKafkaProperties;
import com.aiknowledgeworkspace.workspacecore.outbox.recovery.OutboxRecoveryProperties;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingProperties;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingAsyncConfiguration;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingTriggerMode;
import com.aiknowledgeworkspace.workspacecore.processing.request.ProcessingRequestRelayProperties;
import com.aiknowledgeworkspace.workspacecore.search.configuration.SearchIndexingProperties;
import com.aiknowledgeworkspace.workspacecore.search.configuration.SearchAsyncConfiguration;
import com.aiknowledgeworkspace.workspacecore.search.relay.IndexingRequestRelayProperties;
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

                    ProcessingProperties processing = context.getBean(ProcessingProperties.class);
                    ProcessingRequestRelayProperties processingRelay = context.getBean(
                            ProcessingRequestRelayProperties.class
                    );
                    SearchIndexingProperties indexing = context.getBean(SearchIndexingProperties.class);
                    IndexingRequestRelayProperties indexingRelay = context.getBean(IndexingRequestRelayProperties.class);
                    WorkspaceKafkaProperties kafka = context.getBean(WorkspaceKafkaProperties.class);
                    WorkspaceSecurityProperties security = context.getBean(WorkspaceSecurityProperties.class);
                    FastApiProperties fastApi = context.getBean(FastApiProperties.class);
                    OutboxRecoveryProperties recovery = context.getBean(OutboxRecoveryProperties.class);

                    assertThat(processing.getTriggerMode()).isEqualTo(ProcessingTriggerMode.KAFKA_REQUEST);
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
    void compatibilityProfileKeepsDirectUploadAndAsynchronousControlsDisabled() {
        contextRunner
                .withPropertyValues("spring.profiles.active=compatibility")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    ProcessingProperties processing = context.getBean(ProcessingProperties.class);
                    ProcessingRequestRelayProperties processingRelay = context.getBean(
                            ProcessingRequestRelayProperties.class
                    );
                    SearchIndexingProperties indexing = context.getBean(SearchIndexingProperties.class);
                    IndexingRequestRelayProperties indexingRelay = context.getBean(IndexingRequestRelayProperties.class);
                    WorkspaceKafkaProperties kafka = context.getBean(WorkspaceKafkaProperties.class);
                    WorkspaceSecurityProperties security = context.getBean(WorkspaceSecurityProperties.class);
                    OutboxRecoveryProperties recovery = context.getBean(OutboxRecoveryProperties.class);

                    assertThat(processing.getTriggerMode()).isEqualTo(ProcessingTriggerMode.DIRECT_UPLOAD);
                    assertThat(processingRelay.isEnabled()).isFalse();
                    assertThat(indexing.isAutoRequestEnabled()).isFalse();
                    assertThat(indexingRelay.isEnabled()).isFalse();
                    assertThat(kafka.isEnabled()).isFalse();
                    assertThat(kafka.isProcessingResultListenerEnabled()).isFalse();
                    assertThat(kafka.isIndexingListenerEnabled()).isFalse();
                    assertThat(security.isLegacySessionMode()).isTrue();
                    assertThat(recovery.isEnabled()).isFalse();
                });
    }

    @Test
    void incompleteKafkaRequestConfigurationFailsFastWithSafePropertyNames() {
        contextRunner
                .withPropertyValues("workspace.processing.trigger-mode=kafka_request")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("Incomplete kafka_request configuration")
                            .hasStackTraceContaining("workspace.processing.request-relay.enabled")
                            .hasStackTraceContaining("workspace.kafka.enabled")
                            .hasStackTraceContaining("workspace.kafka.processing-result-listener-enabled")
                            .hasStackTraceContaining("workspace.search.indexing.auto-request-enabled")
                            .hasStackTraceContaining("workspace.search.indexing-relay.enabled")
                            .hasStackTraceContaining("workspace.kafka.indexing-listener-enabled")
                            .hasStackTraceContaining("outbox.recovery.enabled");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            ProcessingProperties.class,
            ProcessingRequestRelayProperties.class,
            SearchIndexingProperties.class,
            IndexingRequestRelayProperties.class,
            WorkspaceKafkaProperties.class,
            WorkspaceSecurityProperties.class,
            OutboxRecoveryProperties.class,
            FastApiProperties.class,
    })
    @Import({
            CoherentAsyncProductProfileValidator.class,
            ProcessingAsyncConfiguration.class,
            SearchAsyncConfiguration.class,
    })
    static class TestConfiguration {
    }
}
