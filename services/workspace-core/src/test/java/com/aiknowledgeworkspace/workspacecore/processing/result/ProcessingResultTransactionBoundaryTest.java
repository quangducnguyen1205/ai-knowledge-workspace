package com.aiknowledgeworkspace.workspacecore.processing.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class ProcessingResultTransactionBoundaryTest {

    @Test
    void kafkaAndManualApplicationEntrypointsKeepRequiredTransactionParticipation() throws Exception {
        assertRequiredTransaction(ProcessingResultEventHandler.class.getMethod("handle", String.class));
        assertRequiredTransaction(ProcessingResultEventHandler.class.getMethod("recoverFailedEvent", UUID.class));
        assertRequiredTransaction(ApplyProcessingResultApplicationService.class.getDeclaredMethod(
                "apply", ApplyProcessingResultCommand.class
        ));
    }

    private void assertRequiredTransaction(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional)
                .as("transaction boundary on %s", method)
                .isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}
