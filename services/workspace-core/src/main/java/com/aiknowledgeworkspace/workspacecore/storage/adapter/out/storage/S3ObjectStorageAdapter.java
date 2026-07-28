package com.aiknowledgeworkspace.workspacecore.storage.adapter.out.storage;

import com.aiknowledgeworkspace.workspacecore.storage.application.exception.ObjectStorageException;

import com.aiknowledgeworkspace.workspacecore.storage.api.ObjectStorageUseCase;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoreObjectCommand;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectMetadata;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectNotFoundException;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectReadException;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectReference;
import java.io.InputStream;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

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

    @Override
    public StoredObjectMetadata stat(StoredObjectReference reference) {
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(reference.bucket())
                    .key(reference.objectKey())
                    .build());
            Long contentLength = response.contentLength();
            if (contentLength == null || contentLength < 0) {
                throw new StoredObjectReadException(
                        "Object storage returned invalid object metadata",
                        new IllegalStateException("content length was missing or negative")
                );
            }
            return new StoredObjectMetadata(contentLength, response.contentType());
        } catch (StoredObjectReadException exception) {
            throw exception;
        } catch (SdkException | IllegalArgumentException exception) {
            throw translateReadFailure("Object storage metadata read failed", exception);
        }
    }

    @Override
    public InputStream openRange(StoredObjectReference reference, long offset, long length) {
        if (offset < 0 || length <= 0) {
            throw new StoredObjectReadException(
                    "Object storage read range was invalid",
                    new IllegalArgumentException("offset must be non-negative and length must be positive")
            );
        }

        final long end;
        try {
            end = Math.addExact(offset, length - 1);
        } catch (ArithmeticException exception) {
            throw new StoredObjectReadException("Object storage read range overflowed", exception);
        }

        try {
            InputStream inputStream = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(reference.bucket())
                    .key(reference.objectKey())
                    .range("bytes=" + offset + "-" + end)
                    .build());
            if (inputStream == null) {
                throw new StoredObjectReadException(
                        "Object storage returned no media stream",
                        new IllegalStateException("object stream was null")
                );
            }
            return inputStream;
        } catch (StoredObjectReadException exception) {
            throw exception;
        } catch (SdkException | IllegalArgumentException exception) {
            throw translateReadFailure("Object storage media read failed", exception);
        }
    }

    private RuntimeException translateReadFailure(String message, RuntimeException exception) {
        if (exception instanceof S3Exception s3Exception && s3Exception.statusCode() == 404) {
            return new StoredObjectNotFoundException("Stored object was not found", exception);
        }
        return new StoredObjectReadException(message, exception);
    }
}
