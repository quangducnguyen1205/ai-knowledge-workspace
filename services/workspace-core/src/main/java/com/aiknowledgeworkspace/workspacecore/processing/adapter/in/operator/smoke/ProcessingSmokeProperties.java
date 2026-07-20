package com.aiknowledgeworkspace.workspacecore.processing.adapter.in.operator.smoke;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workspace.processing.smoke")
public class ProcessingSmokeProperties {

    private ProcessingSmokeCommand command = ProcessingSmokeCommand.NONE;
    private String resultEventFile;
    private UUID requestOutboxEventId;

    public ProcessingSmokeCommand getCommand() {
        return command;
    }

    public void setCommand(ProcessingSmokeCommand command) {
        this.command = command;
    }

    public String getResultEventFile() {
        return resultEventFile;
    }

    public void setResultEventFile(String resultEventFile) {
        this.resultEventFile = resultEventFile;
    }

    public UUID getRequestOutboxEventId() {
        return requestOutboxEventId;
    }

    public void setRequestOutboxEventId(UUID requestOutboxEventId) {
        this.requestOutboxEventId = requestOutboxEventId;
    }
}
