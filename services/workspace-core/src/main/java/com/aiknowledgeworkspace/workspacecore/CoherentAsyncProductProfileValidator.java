package com.aiknowledgeworkspace.workspacecore;

import com.aiknowledgeworkspace.workspacecore.outbox.recovery.OutboxRecoveryProperties;
import com.aiknowledgeworkspace.workspacecore.outbox.WorkspaceKafkaProperties;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingAsyncConfiguration;
import com.aiknowledgeworkspace.workspacecore.search.configuration.SearchAsyncConfiguration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
public class CoherentAsyncProductProfileValidator implements InitializingBean {

    private final ProcessingAsyncConfiguration processingConfiguration;
    private final SearchAsyncConfiguration searchConfiguration;
    private final WorkspaceKafkaProperties kafkaProperties;
    private final OutboxRecoveryProperties outboxRecoveryProperties;

    public CoherentAsyncProductProfileValidator(
            ProcessingAsyncConfiguration processingConfiguration,
            SearchAsyncConfiguration searchConfiguration,
            WorkspaceKafkaProperties kafkaProperties,
            OutboxRecoveryProperties outboxRecoveryProperties
    ) {
        this.processingConfiguration = processingConfiguration;
        this.searchConfiguration = searchConfiguration;
        this.kafkaProperties = kafkaProperties;
        this.outboxRecoveryProperties = outboxRecoveryProperties;
    }

    @Override
    public void afterPropertiesSet() {
        if (!processingConfiguration.isKafkaRequestMode()) {
            return;
        }

        List<String> missingControls = new ArrayList<>();
        require(
                processingConfiguration.isRequestRelayEnabled(),
                "workspace.processing.request-relay.enabled",
                missingControls
        );
        require(kafkaProperties.isEnabled(), "workspace.kafka.enabled", missingControls);
        require(
                kafkaProperties.isProcessingResultListenerEnabled(),
                "workspace.kafka.processing-result-listener-enabled",
                missingControls
        );
        require(
                searchConfiguration.isAutoRequestEnabled(),
                "workspace.search.indexing.auto-request-enabled",
                missingControls
        );
        require(
                searchConfiguration.isIndexingRelayEnabled(),
                "workspace.search.indexing-relay.enabled",
                missingControls
        );
        require(
                kafkaProperties.isIndexingListenerEnabled(),
                "workspace.kafka.indexing-listener-enabled",
                missingControls
        );
        require(outboxRecoveryProperties.isEnabled(), "outbox.recovery.enabled", missingControls);

        if (!missingControls.isEmpty()) {
            throw new IllegalStateException(
                    "Incomplete kafka_request configuration; enable the coherent asynchronous controls: "
                            + String.join(", ", missingControls)
            );
        }
    }

    private void require(boolean condition, String propertyName, List<String> missingControls) {
        if (!condition) {
            missingControls.add(propertyName);
        }
    }
}
