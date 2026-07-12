package com.aiknowledgeworkspace.workspacecore.processing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@ExtendWith(OutputCaptureExtension.class)
class DirectUploadDeprecationReporterTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void directUploadEmitsOneSafeWarningWithoutRejectingStartup(CapturedOutput output) {
        contextRunner.run(context -> assertThat(context).hasNotFailed());

        assertThat(output.getOut() + output.getErr())
                .containsOnlyOnce(DirectUploadDeprecationReporter.WARNING_MESSAGE);
    }

    @Test
    void kafkaRequestDoesNotEmitTheDirectUploadWarning(CapturedOutput output) {
        contextRunner
                .withPropertyValues("workspace.processing.trigger-mode=kafka_request")
                .run(context -> assertThat(context).hasNotFailed());

        assertThat(output.getOut() + output.getErr())
                .doesNotContain(DirectUploadDeprecationReporter.WARNING_MESSAGE);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ProcessingProperties.class)
    @Import(DirectUploadDeprecationReporter.class)
    static class TestConfiguration {
    }
}
