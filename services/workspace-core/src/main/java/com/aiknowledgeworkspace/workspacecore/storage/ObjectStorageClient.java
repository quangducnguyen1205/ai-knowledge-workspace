package com.aiknowledgeworkspace.workspacecore.storage;

public interface ObjectStorageClient {

    StoredObject store(StoreObjectRequest request);

    void delete(String bucket, String objectKey);
}
