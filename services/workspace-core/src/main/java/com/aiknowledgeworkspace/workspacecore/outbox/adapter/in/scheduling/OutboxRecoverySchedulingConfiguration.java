package com.aiknowledgeworkspace.workspacecore.outbox.adapter.in.scheduling;

import com.aiknowledgeworkspace.workspacecore.outbox.application.configuration.OutboxRecoveryProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "outbox.recovery", name = "enabled", havingValue = "true")
class OutboxRecoverySchedulingConfiguration implements SchedulingConfigurer {

    private final OutboxRecoveryProperties properties;
    private final OutboxRecoveryScheduler scheduler;

    OutboxRecoverySchedulingConfiguration(
            OutboxRecoveryProperties properties,
            OutboxRecoveryScheduler scheduler
    ) {
        this.properties = properties;
        this.scheduler = scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(scheduler::reconcileOnSchedule, properties.getInterval());
    }
}
