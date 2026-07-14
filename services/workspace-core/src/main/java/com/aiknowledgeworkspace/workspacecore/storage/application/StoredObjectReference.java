package com.aiknowledgeworkspace.workspacecore.storage.application;

public class StoredObjectReference {

    private final String bucket;
    private final String objectKey;
    private final long sizeBytes;
    private final String contentType;
    private final String eTag;

    public StoredObjectReference(String bucket, String objectKey, long sizeBytes, String contentType, String eTag) {
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.sizeBytes = sizeBytes;
        this.contentType = contentType;
        this.eTag = eTag;
    }

    public String bucket() { return bucket; }
    public String objectKey() { return objectKey; }
    public long sizeBytes() { return sizeBytes; }
    public String contentType() { return contentType; }
    public String eTag() { return eTag; }
}
