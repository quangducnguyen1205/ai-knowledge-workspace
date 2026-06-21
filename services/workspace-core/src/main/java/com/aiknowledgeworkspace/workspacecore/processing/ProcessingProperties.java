package com.aiknowledgeworkspace.workspacecore.processing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workspace.processing")
public class ProcessingProperties {

    private ProcessingTriggerMode triggerMode = ProcessingTriggerMode.DIRECT_UPLOAD;

    public ProcessingTriggerMode getTriggerMode() {
        return triggerMode;
    }

    public void setTriggerMode(ProcessingTriggerMode triggerMode) {
        this.triggerMode = triggerMode;
    }
}
