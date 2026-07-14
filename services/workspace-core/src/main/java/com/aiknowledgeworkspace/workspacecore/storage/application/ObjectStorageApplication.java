package com.aiknowledgeworkspace.workspacecore.storage.application;

/** Product-facing object storage capability. */
public interface ObjectStorageApplication {

    StoredObjectReference store(StoreObjectCommand command);

    void delete(StoredObjectReference reference);
}
