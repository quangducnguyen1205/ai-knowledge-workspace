package com.aiknowledgeworkspace.workspacecore.processing.request;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "workspace.processing.request-relay", name = "enabled", havingValue = "true")
class ProcessingRequestRelaySchedulingConfiguration {
}
