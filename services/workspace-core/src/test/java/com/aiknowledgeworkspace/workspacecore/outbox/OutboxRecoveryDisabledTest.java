package com.aiknowledgeworkspace.workspacecore.outbox;

import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxEventStore;
import com.aiknowledgeworkspace.workspacecore.outbox.recovery.OutboxRecoveryProperties;
import com.aiknowledgeworkspace.workspacecore.outbox.recovery.OutboxRecoveryService;

import com.aiknowledgeworkspace.workspacecore.outbox.application.OutboxRecoveryResult;
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
