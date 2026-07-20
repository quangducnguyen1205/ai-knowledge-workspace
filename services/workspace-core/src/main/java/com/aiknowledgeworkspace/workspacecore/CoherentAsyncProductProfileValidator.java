package com.aiknowledgeworkspace.workspacecore;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("project3")
public class CoherentAsyncProductProfileValidator implements InitializingBean {

    private final boolean processingRequestRelayEnabled;
    private final boolean kafkaEnabled;
    private final boolean processingResultListenerEnabled;
    private final boolean automaticIndexingEnabled;
    private final boolean indexingRelayEnabled;
    private final boolean indexingListenerEnabled;
    private final boolean outboxRecoveryEnabled;

    public CoherentAsyncProductProfileValidator(
            @Value("${workspace.processing.request-relay.enabled:false}") boolean processingRequestRelayEnabled,
            @Value("${workspace.kafka.enabled:false}") boolean kafkaEnabled,
            @Value("${workspace.kafka.processing-result-listener-enabled:false}")
            boolean processingResultListenerEnabled,
            @Value("${workspace.search.indexing.auto-request-enabled:false}") boolean automaticIndexingEnabled,
            @Value("${workspace.search.indexing-relay.enabled:false}") boolean indexingRelayEnabled,
            @Value("${workspace.kafka.indexing-listener-enabled:false}") boolean indexingListenerEnabled,
            @Value("${outbox.recovery.enabled:false}") boolean outboxRecoveryEnabled
    ) {
        this.processingRequestRelayEnabled = processingRequestRelayEnabled;
        this.kafkaEnabled = kafkaEnabled;
        this.processingResultListenerEnabled = processingResultListenerEnabled;
        this.automaticIndexingEnabled = automaticIndexingEnabled;
        this.indexingRelayEnabled = indexingRelayEnabled;
        this.indexingListenerEnabled = indexingListenerEnabled;
        this.outboxRecoveryEnabled = outboxRecoveryEnabled;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> missingControls = new ArrayList<>();
        require(
                processingRequestRelayEnabled,
                "workspace.processing.request-relay.enabled",
                missingControls
        );
        require(kafkaEnabled, "workspace.kafka.enabled", missingControls);
        require(
                processingResultListenerEnabled,
                "workspace.kafka.processing-result-listener-enabled",
                missingControls
        );
        require(
                automaticIndexingEnabled,
                "workspace.search.indexing.auto-request-enabled",
                missingControls
        );
        require(
                indexingRelayEnabled,
                "workspace.search.indexing-relay.enabled",
                missingControls
        );
        require(
                indexingListenerEnabled,
                "workspace.kafka.indexing-listener-enabled",
                missingControls
        );
        require(outboxRecoveryEnabled, "outbox.recovery.enabled", missingControls);

        if (!missingControls.isEmpty()) {
            throw new IllegalStateException(
                    "Incomplete project3 asynchronous configuration; enable the coherent controls: "
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
