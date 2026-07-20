package com.aiknowledgeworkspace.workspacecore.outbox;

import com.aiknowledgeworkspace.workspacecore.outbox.application.configuration.OutboxRecoveryProperties;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class OutboxRecoveryPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void genericDefaultsAreDisabledAndBounded() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            OutboxRecoveryProperties properties = context.getBean(OutboxRecoveryProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getInterval()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.getCooldown()).isEqualTo(Duration.ofSeconds(60));
            assertThat(properties.getBatchSize()).isEqualTo(50);
            assertThat(properties.getMaxCycles()).isEqualTo(3);
        });
    }

    @Test
    void acceptsExplicitValidOverrides() {
        contextRunner
                .withPropertyValues(
                        "outbox.recovery.enabled=true",
                        "outbox.recovery.interval=45s",
                        "outbox.recovery.cooldown=90s",
                        "outbox.recovery.batch-size=25",
                        "outbox.recovery.max-cycles=4"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    OutboxRecoveryProperties properties = context.getBean(OutboxRecoveryProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getInterval()).isEqualTo(Duration.ofSeconds(45));
                    assertThat(properties.getCooldown()).isEqualTo(Duration.ofSeconds(90));
                    assertThat(properties.getBatchSize()).isEqualTo(25);
                    assertThat(properties.getMaxCycles()).isEqualTo(4);
                });
    }

    @Test
    void rejectsInvalidDurationsBatchAndCycleBounds() {
        for (String property : new String[]{
                "outbox.recovery.interval=0s",
                "outbox.recovery.cooldown=-1s",
                "outbox.recovery.batch-size=0",
                "outbox.recovery.batch-size=1001",
                "outbox.recovery.max-cycles=0",
                "outbox.recovery.max-cycles=101",
        }) {
            contextRunner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OutboxRecoveryProperties.class)
    static class TestConfiguration {
    }
}
