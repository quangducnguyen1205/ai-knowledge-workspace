package com.aiknowledgeworkspace.workspacecore.storage.infrastructure.s3;

import com.aiknowledgeworkspace.workspacecore.storage.ObjectStorageClient;
import com.aiknowledgeworkspace.workspacecore.storage.ObjectStorageException;
import com.aiknowledgeworkspace.workspacecore.storage.StoreObjectRequest;
import com.aiknowledgeworkspace.workspacecore.storage.StoredObject;

import com.aiknowledgeworkspace.workspacecore.storage.application.ObjectStorageApplication;
import com.aiknowledgeworkspace.workspacecore.storage.application.StoreObjectCommand;
import com.aiknowledgeworkspace.workspacecore.storage.application.StoredObjectReference;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@Component
public class S3ObjectStorageClient implements ObjectStorageClient, ObjectStorageApplication {

    private final S3Client s3Client;
    private final ObjectKeyFactory objectKeyFactory;
    private final ObjectStorageProperties objectStorageProperties;

    @Autowired
    public S3ObjectStorageClient(
            S3Client s3Client,
            ObjectKeyFactory objectKeyFactory,
            ObjectStorageProperties objectStorageProperties
    ) {
        this.s3Client = s3Client;
        this.objectKeyFactory = objectKeyFactory;
        this.objectStorageProperties = objectStorageProperties;
    }

    public S3ObjectStorageClient(S3Client s3Client) {
        this(s3Client, new ObjectKeyFactory(), new ObjectStorageProperties());
    }

    @Override
    public StoredObjectReference store(StoreObjectCommand command) {
        String objectKey = objectKeyFactory.rawMediaKey(
                command.userId(), command.workspaceId(), command.assetId(), command.originalFilename()
        );
        StoredObject storedObject = store(new StoreObjectRequest(
                objectStorageProperties.getBucket(), objectKey, command.inputStream(),
                command.sizeBytes(), command.contentType()
        ));
        return new StoredObjectReference(
                storedObject.bucket(), storedObject.objectKey(), storedObject.sizeBytes(),
                storedObject.contentType(), storedObject.eTag()
        );
    }

    @Override
    public void delete(StoredObjectReference reference) {
        delete(reference.bucket(), reference.objectKey());
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
