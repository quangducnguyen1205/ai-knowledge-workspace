package com.aiknowledgeworkspace.workspacecore.search.adapter.in.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "workspace.search.indexing-relay", name = "enabled", havingValue = "true")
class IndexingRequestRelaySchedulingConfiguration implements SchedulingConfigurer {

    private final IndexingRequestRelayProperties properties;
    private final IndexingRequestRelayScheduler scheduler;

    IndexingRequestRelaySchedulingConfiguration(
            IndexingRequestRelayProperties properties,
            IndexingRequestRelayScheduler scheduler
    ) {
        this.properties = properties;
        this.scheduler = scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(
                scheduler::relayDueIndexingRequestsOnSchedule,
                properties.getFixedDelay()
        );
    }
}
