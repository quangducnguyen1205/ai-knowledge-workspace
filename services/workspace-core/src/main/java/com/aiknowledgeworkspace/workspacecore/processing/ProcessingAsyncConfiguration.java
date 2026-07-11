package com.aiknowledgeworkspace.workspacecore.processing;

import com.aiknowledgeworkspace.workspacecore.processing.request.ProcessingRequestRelayProperties;
import org.springframework.stereotype.Component;

@Component
public class ProcessingAsyncConfiguration {

    private final ProcessingProperties processingProperties;
    private final ProcessingRequestRelayProperties requestRelayProperties;

    public ProcessingAsyncConfiguration(
            ProcessingProperties processingProperties,
            ProcessingRequestRelayProperties requestRelayProperties
    ) {
        this.processingProperties = processingProperties;
        this.requestRelayProperties = requestRelayProperties;
    }

    public boolean isKafkaRequestMode() {
        return processingProperties.getTriggerMode() == ProcessingTriggerMode.KAFKA_REQUEST;
    }

    public boolean isRequestRelayEnabled() {
        return requestRelayProperties.isEnabled();
    }
}
