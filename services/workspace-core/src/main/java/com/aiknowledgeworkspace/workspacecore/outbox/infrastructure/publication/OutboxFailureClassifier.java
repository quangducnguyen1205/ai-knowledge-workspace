package com.aiknowledgeworkspace.workspacecore.outbox.infrastructure.publication;

import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxFailureClassification;
import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxFailureDisposition;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.BrokerNotAvailableException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class OutboxFailureClassifier {

    private static final int MAX_CAUSE_DEPTH = 32;

    public OutboxFailureClassification classify(@Nullable Throwable throwable) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = throwable;
        OutboxFailureClassification transientClassification = null;
        int depth = 0;

        while (current != null && depth++ < MAX_CAUSE_DEPTH && visited.add(current)) {
            OutboxFailureClassification permanent = classifyPermanent(current);
            if (permanent != null) {
                return permanent;
            }
            if (transientClassification == null) {
                transientClassification = classifyTransient(current);
            }
            current = current.getCause();
        }

        if (transientClassification != null) {
            return transientClassification;
        }
        return new OutboxFailureClassification(OutboxFailureDisposition.UNKNOWN, "UNKNOWN_PUBLICATION_FAILURE");
    }

    @Nullable
    private OutboxFailureClassification classifyPermanent(Throwable throwable) {
        if (throwable instanceof PermanentOutboxPublishException) {
            return permanent("INVALID_OUTBOX_EVENT");
        }
        if (throwable instanceof JsonProcessingException || throwable instanceof SerializationException) {
            return permanent("SERIALIZATION_FAILURE");
        }
        if (throwable instanceof AuthenticationException || throwable instanceof AuthorizationException) {
            return permanent("KAFKA_AUTH_CONFIGURATION_FAILURE");
        }
        if (throwable instanceof InvalidTopicException
                || throwable instanceof ProducerFencedException
                || throwable instanceof ConfigException) {
            return permanent("KAFKA_CONFIGURATION_FAILURE");
        }
        return null;
    }

    @Nullable
    private OutboxFailureClassification classifyTransient(Throwable throwable) {
        if (throwable instanceof RetriableException || throwable instanceof BrokerNotAvailableException) {
            return transientFailure("KAFKA_RETRYABLE_FAILURE");
        }
        if (throwable instanceof java.util.concurrent.TimeoutException
                || throwable instanceof SocketTimeoutException) {
            return transientFailure("PUBLICATION_TIMEOUT");
        }
        if (throwable instanceof ConnectException) {
            return transientFailure("BROKER_CONNECTION_FAILURE");
        }
        return null;
    }

    private OutboxFailureClassification permanent(String safeCategory) {
        return new OutboxFailureClassification(OutboxFailureDisposition.PERMANENT, safeCategory);
    }

    private OutboxFailureClassification transientFailure(String safeCategory) {
        return new OutboxFailureClassification(OutboxFailureDisposition.TRANSIENT, safeCategory);
    }
}
