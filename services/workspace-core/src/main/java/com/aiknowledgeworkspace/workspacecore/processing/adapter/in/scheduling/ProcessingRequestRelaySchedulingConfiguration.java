package com.aiknowledgeworkspace.workspacecore.processing.adapter.in.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "workspace.processing.request-relay", name = "enabled", havingValue = "true")
class ProcessingRequestRelaySchedulingConfiguration implements SchedulingConfigurer {

    private final ProcessingRequestRelayProperties properties;
    private final ProcessingRequestRelayScheduler scheduler;

    ProcessingRequestRelaySchedulingConfiguration(
            ProcessingRequestRelayProperties properties,
            ProcessingRequestRelayScheduler scheduler
    ) {
        this.properties = properties;
        this.scheduler = scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(
                scheduler::relayDueRequestsOnSchedule,
                properties.getFixedDelay()
        );
    }
}
