package com.aiknowledgeworkspace.workspacecore.storage;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@Component
public class S3ObjectStorageClient implements ObjectStorageClient {

    private final S3Client s3Client;

    public S3ObjectStorageClient(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public StoredObject store(StoreObjectRequest request) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(request.bucket())
                    .key(request.objectKey())
                    .contentLength(request.sizeBytes())
                    .contentType(request.contentType())
                    .build();

            PutObjectResponse response = s3Client.putObject(
                    putObjectRequest,
                    RequestBody.fromInputStream(request.inputStream(), request.sizeBytes())
            );

            return new StoredObject(
                    request.bucket(),
                    request.objectKey(),
                    request.sizeBytes(),
                    request.contentType(),
                    response.eTag()
            );
        } catch (SdkException | IllegalArgumentException exception) {
            throw new ObjectStorageException("Object storage upload failed", exception);
        }
    }

    @Override
    public void delete(String bucket, String objectKey) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build();
            s3Client.deleteObject(deleteObjectRequest);
        } catch (SdkException | IllegalArgumentException exception) {
            throw new ObjectStorageException("Object storage delete failed", exception);
        }
    }
}
