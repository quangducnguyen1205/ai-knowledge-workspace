package com.aiknowledgeworkspace.workspacecore.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxRecoveryDisabledTest {

    @Test
    void disabledRecoveryDoesNotQueryOrMutateOutboxRows() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
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
