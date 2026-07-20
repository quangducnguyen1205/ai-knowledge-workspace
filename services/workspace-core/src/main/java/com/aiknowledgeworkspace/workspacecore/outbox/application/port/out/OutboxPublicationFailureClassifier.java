package com.aiknowledgeworkspace.workspacecore.outbox.application.port.out;

import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxFailureClassification;

public interface OutboxPublicationFailureClassifier {

    OutboxFailureClassification classify(Throwable failure);
}
