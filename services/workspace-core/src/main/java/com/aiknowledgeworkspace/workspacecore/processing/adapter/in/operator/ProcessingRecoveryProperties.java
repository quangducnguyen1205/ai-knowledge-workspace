package com.aiknowledgeworkspace.workspacecore.processing.adapter.in.operator;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workspace.processing.recovery")
public class ProcessingRecoveryProperties {

    private ProcessingRecoveryCommand command = ProcessingRecoveryCommand.NONE;
    private UUID resultEventId;
    private UUID outboxEventId;
    private Duration minimumPublishingAge = Duration.ofMinutes(5);

    public ProcessingRecoveryCommand getCommand() {
        return command;
    }

    public void setCommand(ProcessingRecoveryCommand command) {
        this.command = command;
    }

    public UUID getResultEventId() {
        return resultEventId;
    }

    public void setResultEventId(UUID resultEventId) {
        this.resultEventId = resultEventId;
    }

    public UUID getOutboxEventId() {
        return outboxEventId;
    }

    public void setOutboxEventId(UUID outboxEventId) {
        this.outboxEventId = outboxEventId;
    }

    public Duration getMinimumPublishingAge() {
        return minimumPublishingAge;
    }

    public void setMinimumPublishingAge(Duration minimumPublishingAge) {
        this.minimumPublishingAge = minimumPublishingAge;
    }
}
