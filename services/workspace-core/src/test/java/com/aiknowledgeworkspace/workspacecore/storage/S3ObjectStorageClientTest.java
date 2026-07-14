package com.aiknowledgeworkspace.workspacecore.storage;

import com.aiknowledgeworkspace.workspacecore.storage.infrastructure.s3.S3ObjectStorageClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class S3ObjectStorageClientTest {

    @Mock
    private S3Client s3Client;

    @Test
    void storePutsObjectAndReturnsStoredMetadata() {
        S3ObjectStorageClient storageClient = new S3ObjectStorageClient(s3Client);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("\"etag-1\"").build());

        StoredObject storedObject = storageClient.store(new StoreObjectRequest(
                "workspace-media",
                "users/user-1/workspaces/workspace/assets/asset/raw/lecture.mp4",
                new ByteArrayInputStream("video-bytes".getBytes()),
                11L,
                "video/mp4"
        ));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        assertThat(requestCaptor.getValue().bucket()).isEqualTo("workspace-media");
        assertThat(requestCaptor.getValue().key())
                .isEqualTo("users/user-1/workspaces/workspace/assets/asset/raw/lecture.mp4");
        assertThat(requestCaptor.getValue().contentLength()).isEqualTo(11L);
        assertThat(requestCaptor.getValue().contentType()).isEqualTo("video/mp4");
        assertThat(storedObject.eTag()).isEqualTo("\"etag-1\"");
        assertThat(storedObject.sizeBytes()).isEqualTo(11L);
    }

    @Test
    void deleteDeletesObjectByBucketAndKey() {
        S3ObjectStorageClient storageClient = new S3ObjectStorageClient(s3Client);
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        storageClient.delete("workspace-media", "objects/raw.mp4");

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("workspace-media");
        assertThat(requestCaptor.getValue().key()).isEqualTo("objects/raw.mp4");
    }

    @Test
    void storeWrapsS3Exception() {
        S3ObjectStorageClient storageClient = new S3ObjectStorageClient(s3Client);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("s3 put failed").build());

        assertThatThrownBy(() -> storageClient.store(new StoreObjectRequest(
                "workspace-media",
                "objects/raw.mp4",
                new ByteArrayInputStream("video-bytes".getBytes()),
                11L,
                "video/mp4"
        )))
                .isInstanceOf(ObjectStorageException.class)
                .hasMessage("Object storage upload failed")
                .hasCauseInstanceOf(S3Exception.class);
    }

    @Test
    void deleteWrapsS3Exception() {
        S3ObjectStorageClient storageClient = new S3ObjectStorageClient(s3Client);
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("s3 delete failed").build());

        assertThatThrownBy(() -> storageClient.delete("workspace-media", "objects/raw.mp4"))
                .isInstanceOf(ObjectStorageException.class)
                .hasMessage("Object storage delete failed")
                .hasCauseInstanceOf(S3Exception.class);
    }
}
