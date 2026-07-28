package com.aiknowledgeworkspace.workspacecore.storage.api;

import java.io.InputStream;

/** Product-facing object storage capability. */
public interface ObjectStorageUseCase {

    StoredObjectReference store(StoreObjectCommand command);

    void delete(StoredObjectReference reference);

    StoredObjectMetadata stat(StoredObjectReference reference);

    InputStream openRange(StoredObjectReference reference, long offset, long length);
}
