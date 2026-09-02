package com.aiknowledgeworkspace.workspacecore.search.adapter.in.scheduling;

import com.aiknowledgeworkspace.workspacecore.search.application.configuration.SearchIndexingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "workspace.search.indexing", name = "recovery-enabled", havingValue = "true")
class StaleIndexingRecoverySchedulingConfiguration implements SchedulingConfigurer {

    private final SearchIndexingProperties properties;
    private final StaleIndexingRecoveryScheduler scheduler;

    StaleIndexingRecoverySchedulingConfiguration(
            SearchIndexingProperties properties,
            StaleIndexingRecoveryScheduler scheduler
    ) {
        this.properties = properties;
        this.scheduler = scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(
                scheduler::recoverStaleIndexingJobsOnSchedule,
                properties.getRecoveryInterval()
        );
    }
}
