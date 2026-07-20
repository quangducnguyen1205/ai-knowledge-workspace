package com.aiknowledgeworkspace.workspacecore.outbox;

import com.aiknowledgeworkspace.workspacecore.outbox.application.port.out.OutboxEventStore;
import com.aiknowledgeworkspace.workspacecore.outbox.application.configuration.OutboxRecoveryProperties;
import com.aiknowledgeworkspace.workspacecore.outbox.application.service.OutboxRecoveryService;

import com.aiknowledgeworkspace.workspacecore.outbox.api.OutboxRecoveryResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxRecoveryDisabledTest {

    @Test
    void disabledRecoveryDoesNotQueryOrMutateOutboxRows() {
        OutboxEventStore repository = mock(OutboxEventStore.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        OutboxRecoveryProperties properties = new OutboxRecoveryProperties();
        OutboxRecoveryService service = new OutboxRecoveryService(
                repository,
                properties,
                transactionTemplate,
                Clock.systemUTC()
        );

        OutboxRecoveryResult result = service.reconcileEligibleFailures();

        assertThat(result.disabled()).isTrue();
        assertThat(result.requeued()).isZero();
        verifyNoInteractions(repository, transactionTemplate);
    }
}
