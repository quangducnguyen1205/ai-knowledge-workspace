package com.aiknowledgeworkspace.workspacecore.storage;

import com.aiknowledgeworkspace.workspacecore.storage.application.exception.ObjectStorageException;
import com.aiknowledgeworkspace.workspacecore.storage.adapter.out.storage.S3ObjectStorageAdapter;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoreObjectCommand;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectMetadata;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectNotFoundException;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectReadException;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class S3ObjectStorageAdapterTest {

    @Mock
    private S3Client s3Client;

    @Test
    void storePutsObjectAndReturnsStoredMetadata() {
        S3ObjectStorageAdapter storageClient = new S3ObjectStorageAdapter(s3Client);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("\"etag-1\"").build());

        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        StoredObjectReference storedObject = storageClient.store(new StoreObjectCommand(
                "user-1",
                workspaceId,
                assetId,
                "lecture.mp4",
                new ByteArrayInputStream("video-bytes".getBytes()),
                11L,
                "video/mp4"
        ));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        assertThat(requestCaptor.getValue().bucket()).isEqualTo("workspace-media");
        assertThat(requestCaptor.getValue().key())
                .isEqualTo("users/user-1/workspaces/" + workspaceId + "/assets/" + assetId + "/raw/lecture.mp4");
        assertThat(requestCaptor.getValue().contentLength()).isEqualTo(11L);
        assertThat(requestCaptor.getValue().contentType()).isEqualTo("video/mp4");
        assertThat(storedObject.eTag()).isEqualTo("\"etag-1\"");
        assertThat(storedObject.sizeBytes()).isEqualTo(11L);
    }

    @Test
    void deleteDeletesObjectByBucketAndKey() {
        S3ObjectStorageAdapter storageClient = new S3ObjectStorageAdapter(s3Client);
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        storageClient.delete(new StoredObjectReference(
                "workspace-media", "objects/raw.mp4", 1L, "video/mp4", null
        ));

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("workspace-media");
        assertThat(requestCaptor.getValue().key()).isEqualTo("objects/raw.mp4");
    }

    @Test
    void storeWrapsS3Exception() {
        S3ObjectStorageAdapter storageClient = new S3ObjectStorageAdapter(s3Client);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("s3 put failed").build());

        assertThatThrownBy(() -> storageClient.store(new StoreObjectCommand(
                "user-1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "lecture.mp4",
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
        S3ObjectStorageAdapter storageClient = new S3ObjectStorageAdapter(s3Client);
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("s3 delete failed").build());

        assertThatThrownBy(() -> storageClient.delete(new StoredObjectReference(
                "workspace-media", "objects/raw.mp4", 1L, "video/mp4", null
        )))
                .isInstanceOf(ObjectStorageException.class)
                .hasMessage("Object storage delete failed")
                .hasCauseInstanceOf(S3Exception.class);
    }

    @Test
    void statReadsOnlyBoundedObjectMetadata() {
        S3ObjectStorageAdapter storageClient = new S3ObjectStorageAdapter(s3Client);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder()
                        .contentLength(11L)
                        .contentType("video/mp4")
                        .eTag("\"etag-1\"")
                        .build()
        );

        StoredObjectMetadata metadata = storageClient.stat(reference());

        ArgumentCaptor<HeadObjectRequest> requestCaptor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("workspace-media");
        assertThat(requestCaptor.getValue().key()).isEqualTo("objects/raw.mp4");
        assertThat(metadata.sizeBytes()).isEqualTo(11L);
        assertThat(metadata.contentType()).isEqualTo("video/mp4");
    }

    @Test
    void openRangeUsesExactS3ByteRangeWithoutBuffering() throws Exception {
        S3ObjectStorageAdapter storageClient = new S3ObjectStorageAdapter(s3Client);
        ResponseInputStream<GetObjectResponse> responseInputStream = new ResponseInputStream<>(
                GetObjectResponse.builder().contentLength(3L).build(),
                AbortableInputStream.create(new ByteArrayInputStream(new byte[]{2, 3, 4}))
        );
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);

        InputStream actual = storageClient.openRange(reference(), 2, 3);

        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("workspace-media");
        assertThat(requestCaptor.getValue().key()).isEqualTo("objects/raw.mp4");
        assertThat(requestCaptor.getValue().range()).isEqualTo("bytes=2-4");
        assertThat(actual.read()).isEqualTo(2);
        actual.close();
    }

    @Test
    void statAndRangeReadTranslateMissingObjectWithoutLeakingSdkFailure() {
        S3ObjectStorageAdapter storageClient = new S3ObjectStorageAdapter(s3Client);
        S3Exception.Builder missingBuilder = S3Exception.builder();
        missingBuilder.statusCode(404);
        S3Exception missing = (S3Exception) missingBuilder.build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(missing);

        assertThatThrownBy(() -> storageClient.stat(reference()))
                .isInstanceOf(StoredObjectNotFoundException.class)
                .hasMessage("Stored object was not found")
                .hasMessageNotContaining("secret");

        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(missing);
        assertThatThrownBy(() -> storageClient.openRange(reference(), 0, 1))
                .isInstanceOf(StoredObjectNotFoundException.class)
                .hasMessage("Stored object was not found")
                .hasMessageNotContaining("secret");
    }

    @Test
    void statAndRangeReadTranslateStorageAvailabilityAndOverflowFailures() {
        S3ObjectStorageAdapter storageClient = new S3ObjectStorageAdapter(s3Client);
        S3Exception.Builder unavailableBuilder = S3Exception.builder();
        unavailableBuilder.statusCode(503);
        S3Exception unavailable = (S3Exception) unavailableBuilder.build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(unavailable);

        assertThatThrownBy(() -> storageClient.stat(reference()))
                .isInstanceOf(StoredObjectReadException.class)
                .hasMessage("Object storage metadata read failed")
                .hasMessageNotContaining("internal endpoint");

        assertThatThrownBy(() -> storageClient.openRange(reference(), Long.MAX_VALUE, 2))
                .isInstanceOf(StoredObjectReadException.class)
                .hasMessage("Object storage read range overflowed");
    }

    private StoredObjectReference reference() {
        return new StoredObjectReference(
                "workspace-media",
                "objects/raw.mp4",
                11L,
                "video/mp4",
                "\"etag-1\""
        );
    }
}
