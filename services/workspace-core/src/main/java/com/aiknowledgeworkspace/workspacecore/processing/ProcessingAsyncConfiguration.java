package com.aiknowledgeworkspace.workspacecore.processing;

import com.aiknowledgeworkspace.workspacecore.processing.request.ProcessingRequestRelayProperties;
import org.springframework.stereotype.Component;

@Component
public class ProcessingAsyncConfiguration {

    private final ProcessingRequestRelayProperties requestRelayProperties;

    public ProcessingAsyncConfiguration(ProcessingRequestRelayProperties requestRelayProperties) {
        this.requestRelayProperties = requestRelayProperties;
    }

    public boolean isRequestRelayEnabled() {
        return requestRelayProperties.isEnabled();
    }
}
