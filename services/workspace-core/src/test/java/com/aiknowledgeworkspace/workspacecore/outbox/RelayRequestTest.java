package com.aiknowledgeworkspace.workspacecore.outbox;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiknowledgeworkspace.workspacecore.outbox.api.RelayExecutionPolicy;
import com.aiknowledgeworkspace.workspacecore.outbox.api.RelayRequest;
import com.aiknowledgeworkspace.workspacecore.outbox.api.RelaySelection;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RelayRequestTest {

    @Test
    void scheduledFactoriesExposeOnlyTheirLegalPolicies() {
        RelayRequest global = RelayRequest.scheduledAll(25);
        RelayRequest scoped = RelayRequest.scheduledForType("asset.processing.requested", 10);

        assertThat(global.selection()).isEqualTo(new RelaySelection.AllDue(25));
        assertThat(global.executionPolicy()).isEqualTo(RelayExecutionPolicy.SCHEDULED_GLOBAL);
        assertThat(scoped.selection())
                .isEqualTo(new RelaySelection.DueByType("asset.processing.requested", 10));
        assertThat(scoped.executionPolicy()).isEqualTo(RelayExecutionPolicy.SCHEDULED_SCOPED);
    }

    @Test
    void explicitFactoryRequiresAnExactEventSelection() {
        UUID eventId = UUID.randomUUID();

        RelayRequest request = RelayRequest.explicit(eventId, "asset.processing.requested", "wrong type");

        assertThat(request.selection()).isEqualTo(
                new RelaySelection.ExactEvent(eventId, "asset.processing.requested", "wrong type")
        );
        assertThat(request.executionPolicy()).isEqualTo(RelayExecutionPolicy.EXPLICIT_OPERATOR);
    }

    @Test
    void impossibleSelectionAndPolicyCombinationsAreRejected() {
        RelaySelection allDue = new RelaySelection.AllDue(10);
        RelaySelection exact = new RelaySelection.ExactEvent(
                UUID.randomUUID(),
                "asset.processing.requested",
                "wrong type"
        );

        assertThatThrownBy(() -> new RelayRequest(allDue, RelayExecutionPolicy.EXPLICIT_OPERATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact selection");
        assertThatThrownBy(() -> new RelayRequest(exact, RelayExecutionPolicy.SCHEDULED_GLOBAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact selection");
    }

    @Test
    void invalidSelectionValuesAreRejectedAtConstructionTime() {
        assertThatThrownBy(() -> RelayRequest.scheduledAll(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize");
        assertThatThrownBy(() -> RelayRequest.scheduledForType(" ", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventType");
    }
}
