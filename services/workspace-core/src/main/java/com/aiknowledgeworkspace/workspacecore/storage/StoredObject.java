package com.aiknowledgeworkspace.workspacecore.storage;

public class StoredObject extends com.aiknowledgeworkspace.workspacecore.storage.application.StoredObjectReference {
    public StoredObject(String bucket, String objectKey, long sizeBytes, String contentType, String eTag) {
        super(bucket, objectKey, sizeBytes, contentType, eTag);
    }
}
