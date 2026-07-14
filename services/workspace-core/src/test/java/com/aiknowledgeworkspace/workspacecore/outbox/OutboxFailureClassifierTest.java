package com.aiknowledgeworkspace.workspacecore.outbox;

import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxFailureClassification;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxFailureDisposition;
import com.aiknowledgeworkspace.workspacecore.outbox.infrastructure.publication.OutboxFailureClassifier;
import com.aiknowledgeworkspace.workspacecore.outbox.infrastructure.publication.OutboxPublishException;
import com.aiknowledgeworkspace.workspacecore.outbox.infrastructure.publication.PermanentOutboxPublishException;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.common.errors.BrokerNotAvailableException;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.Test;

class OutboxFailureClassifierTest {

    private final OutboxFailureClassifier classifier = new OutboxFailureClassifier();

    @Test
    void classifiesRetryableBrokerFailureAsTransient() {
        assertThat(classifier.classify(new BrokerNotAvailableException("broker unavailable")))
                .isEqualTo(new OutboxFailureClassification(
                        OutboxFailureDisposition.TRANSIENT,
                        "KAFKA_RETRYABLE_FAILURE"
                ));
    }

    @Test
    void classifiesTimeoutAndConnectionFailuresAsTransient() {
        assertThat(classifier.classify(new TimeoutException("timed out")).disposition())
                .isEqualTo(OutboxFailureDisposition.TRANSIENT);
        assertThat(classifier.classify(new ConnectException("connection refused")).disposition())
                .isEqualTo(OutboxFailureDisposition.TRANSIENT);
    }

    @Test
    void traversesWrappedRetryableFailure() {
        OutboxPublishException wrapped = new OutboxPublishException(
                "publish failed",
                new BrokerNotAvailableException("broker unavailable")
        );

        assertThat(classifier.classify(wrapped).disposition()).isEqualTo(OutboxFailureDisposition.TRANSIENT);
    }

    @Test
    void classifiesSerializationAndInvalidEnvelopeFailuresAsPermanent() {
        assertThat(classifier.classify(new SerializationException("invalid value")).disposition())
                .isEqualTo(OutboxFailureDisposition.PERMANENT);
        assertThat(classifier.classify(new PermanentOutboxPublishException("invalid event")).disposition())
                .isEqualTo(OutboxFailureDisposition.PERMANENT);
    }

    @Test
    void unknownAndNullFailuresFailClosed() {
        assertThat(classifier.classify(new IllegalStateException("unexpected")).disposition())
                .isEqualTo(OutboxFailureDisposition.UNKNOWN);
        assertThat(classifier.classify(null).disposition()).isEqualTo(OutboxFailureDisposition.UNKNOWN);
    }

    @Test
    void cyclicCauseChainIsBoundedSafely() {
        RuntimeException cyclic = new RuntimeException("cyclic") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };

        assertThat(classifier.classify(cyclic).disposition()).isEqualTo(OutboxFailureDisposition.UNKNOWN);
    }
}
