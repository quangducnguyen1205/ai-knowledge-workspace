package com.aiknowledgeworkspace.workspacecore.outbox.application;

import com.aiknowledgeworkspace.workspacecore.outbox.domain.OutboxFailureClassification;

public interface OutboxPublicationFailureClassifier {

    OutboxFailureClassification classify(Throwable failure);
}
