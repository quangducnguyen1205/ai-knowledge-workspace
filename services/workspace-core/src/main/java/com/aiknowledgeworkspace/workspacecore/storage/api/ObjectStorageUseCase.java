package com.aiknowledgeworkspace.workspacecore.storage.api;

/** Product-facing object storage capability. */
public interface ObjectStorageUseCase {

    StoredObjectReference store(StoreObjectCommand command);

    void delete(StoredObjectReference reference);
}
