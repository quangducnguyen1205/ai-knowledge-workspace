package com.aiknowledgeworkspace.workspacecore.storage.adapter.out.storage;

import com.aiknowledgeworkspace.workspacecore.storage.application.exception.ObjectStorageException;

import com.aiknowledgeworkspace.workspacecore.storage.api.ObjectStorageUseCase;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoreObjectCommand;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectReference;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@Component
public class S3ObjectStorageAdapter implements ObjectStorageUseCase {

    private final S3Client s3Client;
    private final ObjectKeyFactory objectKeyFactory;
    private final ObjectStorageProperties objectStorageProperties;

    @Autowired
    public S3ObjectStorageAdapter(
            S3Client s3Client,
            ObjectKeyFactory objectKeyFactory,
            ObjectStorageProperties objectStorageProperties
    ) {
        this.s3Client = s3Client;
        this.objectKeyFactory = objectKeyFactory;
        this.objectStorageProperties = objectStorageProperties;
    }

    public S3ObjectStorageAdapter(S3Client s3Client) {
        this(s3Client, new ObjectKeyFactory(), new ObjectStorageProperties());
    }

    @Override
    public StoredObjectReference store(StoreObjectCommand command) {
        String objectKey = objectKeyFactory.rawMediaKey(
                command.userId(), command.workspaceId(), command.assetId(), command.originalFilename()
        );
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(objectStorageProperties.getBucket())
                    .key(objectKey)
                    .contentLength(command.sizeBytes())
                    .contentType(command.contentType())
                    .build();

            PutObjectResponse response = s3Client.putObject(
                    putObjectRequest,
                    RequestBody.fromInputStream(command.inputStream(), command.sizeBytes())
            );

            return new StoredObjectReference(
                    objectStorageProperties.getBucket(),
                    objectKey,
                    command.sizeBytes(),
                    command.contentType(),
                    response.eTag()
            );
        } catch (SdkException | IllegalArgumentException exception) {
            throw new ObjectStorageException("Object storage upload failed", exception);
        }
    }

    @Override
    public void delete(StoredObjectReference reference) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(reference.bucket())
                    .key(reference.objectKey())
                    .build();
            s3Client.deleteObject(deleteObjectRequest);
        } catch (SdkException | IllegalArgumentException exception) {
            throw new ObjectStorageException("Object storage delete failed", exception);
        }
    }
}
