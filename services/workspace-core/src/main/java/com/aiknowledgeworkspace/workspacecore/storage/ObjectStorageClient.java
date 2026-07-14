package com.aiknowledgeworkspace.workspacecore.storage;

import com.aiknowledgeworkspace.workspacecore.storage.application.ObjectStorageApplication;
import com.aiknowledgeworkspace.workspacecore.storage.application.StoreObjectCommand;
import com.aiknowledgeworkspace.workspacecore.storage.application.StoredObjectReference;

/** Internal compatibility type retained for storage-focused tests and adapters. */
public interface ObjectStorageClient extends ObjectStorageApplication {

    StoredObject store(StoreObjectRequest request);

    void delete(String bucket, String objectKey);

    @Override
    default StoredObjectReference store(StoreObjectCommand command) {
        throw new UnsupportedOperationException("Raw object storage application API is not implemented");
    }

    @Override
    default void delete(StoredObjectReference reference) {
        delete(reference.bucket(), reference.objectKey());
    }
}
