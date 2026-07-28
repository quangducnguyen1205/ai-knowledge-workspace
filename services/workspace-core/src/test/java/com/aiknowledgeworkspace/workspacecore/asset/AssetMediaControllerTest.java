package com.aiknowledgeworkspace.workspacecore.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web.AssetApiExceptionHandler;
import com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web.AssetMediaController;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetMediaNotAvailableException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetMediaReadException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetMediaDescriptor;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.AssetMediaUseCase;
import com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectReference;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AssetMediaControllerTest {

    private static final byte[] MEDIA_BYTES = "0123456789".getBytes(StandardCharsets.UTF_8);

    private AssetMediaUseCase assetMedia;
    private AssetMediaController controller;
    private MockMvc mockMvc;
    private UUID assetId;
    private AssetMediaDescriptor descriptor;

    @BeforeEach
    void setUp() {
        assetMedia = mock(AssetMediaUseCase.class);
        controller = new AssetMediaController(assetMedia);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AssetApiExceptionHandler())
                .build();
        assetId = UUID.randomUUID();
        descriptor = descriptor(MEDIA_BYTES.length, "video/mp4", "lecture.mp4");
        when(assetMedia.resolve(assetId)).thenReturn(descriptor);
        when(assetMedia.openStream(eq(descriptor), anyLong(), anyLong())).thenAnswer(invocation -> {
            int start = Math.toIntExact(invocation.getArgument(1, Long.class));
            int length = Math.toIntExact(invocation.getArgument(2, Long.class));
            return new ByteArrayInputStream(Arrays.copyOfRange(MEDIA_BYTES, start, start + length));
        });
    }

    @Test
    void fullGetStreamsExactBytesWithAuthorizedPrivateHeaders() throws Exception {
        performStreaming(get("/api/assets/{assetId}/media", assetId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp4"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, MEDIA_BYTES.length))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(content().bytes(MEDIA_BYTES));

        verify(assetMedia).openStream(descriptor, 0, MEDIA_BYTES.length);
    }

    @ParameterizedTest
    @CsvSource({
            "bytes=0-0,0,0,0",
            "bytes=2-5,2,5,2345",
            "bytes=6-,6,9,6789",
            "bytes=-3,7,9,789",
            "bytes=9-9,9,9,9",
            "bytes=0-999,0,9,0123456789"
    })
    void rangeGetStreamsOnlyResolvedBytes(
            String rangeHeader,
            long expectedStart,
            long expectedEnd,
            String expectedBody
    ) throws Exception {
        performStreaming(get("/api/assets/{assetId}/media", assetId)
                        .header(HttpHeaders.RANGE, rangeHeader))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_RANGE,
                        "bytes " + expectedStart + "-" + expectedEnd + "/" + MEDIA_BYTES.length
                ))
                .andExpect(header().longValue(
                        HttpHeaders.CONTENT_LENGTH,
                        expectedEnd - expectedStart + 1
                ))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(content().string(expectedBody));

        verify(assetMedia).openStream(descriptor, expectedStart, expectedEnd - expectedStart + 1);
    }

    @Test
    void oneByteObjectSupportsFullAndRangeResponses() throws Exception {
        byte[] oneByte = new byte[]{42};
        AssetMediaDescriptor oneByteDescriptor = descriptor(1, "video/mp4", "one.mp4");
        when(assetMedia.resolve(assetId)).thenReturn(oneByteDescriptor);
        when(assetMedia.openStream(oneByteDescriptor, 0, 1))
                .thenAnswer(invocation -> new ByteArrayInputStream(oneByte));

        performStreaming(get("/api/assets/{assetId}/media", assetId)
                        .header(HttpHeaders.RANGE, "bytes=0-0"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-0/1"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 1))
                .andExpect(content().bytes(oneByte));
    }

    @Test
    void headReturnsMetadataWithoutOpeningTheObjectBody() throws Exception {
        mockMvc.perform(head("/api/assets/{assetId}/media", assetId)
                        .header(HttpHeaders.RANGE, "bytes=2-5"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp4"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, MEDIA_BYTES.length))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(content().bytes(new byte[0]));

        verify(assetMedia, never()).openStream(eq(descriptor), anyLong(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "bytes=abc",
            "bytes=0-0,2-2",
            "bytes=10-",
            "bytes=9223372036854775807-",
            "bytes=0-9223372036854775808"
    })
    void invalidOrUnsupportedRangeReturnsBounded416(String rangeHeader) throws Exception {
        mockMvc.perform(get("/api/assets/{assetId}/media", assetId)
                        .header(HttpHeaders.RANGE, rangeHeader))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */10"))
                .andExpect(jsonPath("$.code").value("ASSET_MEDIA_RANGE_NOT_SATISFIABLE"));

        verify(assetMedia, never()).openStream(eq(descriptor), anyLong(), anyLong());
    }

    @Test
    void missingUnauthorizedAndYoutubeMediaUseBoundedNotFoundResponses() throws Exception {
        when(assetMedia.resolve(assetId)).thenThrow(new AssetNotFoundException());
        mockMvc.perform(get("/api/assets/{assetId}/media", assetId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSET_NOT_FOUND"));

        UUID youtubeAssetId = UUID.randomUUID();
        when(assetMedia.resolve(youtubeAssetId)).thenThrow(new AssetMediaNotAvailableException());
        mockMvc.perform(get("/api/assets/{assetId}/media", youtubeAssetId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSET_MEDIA_NOT_AVAILABLE"));
    }

    @Test
    void storageFailureReturns503WithoutStorageIdentity() throws Exception {
        when(assetMedia.resolve(assetId)).thenThrow(
                new AssetMediaReadException()
        );

        mockMvc.perform(get("/api/assets/{assetId}/media", assetId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ASSET_MEDIA_READ_FAILED"))
                .andExpect(content().string(not(containsString("workspace-media"))))
                .andExpect(content().string(not(containsString("objects/secret.mp4"))));
    }

    @Test
    void unsafeMetadataFallsBackAndCannotInjectResponseHeaders() throws Exception {
        AssetMediaDescriptor unsafe = descriptor(10, "video/mp4\r\nX-Evil: yes", "lecture\r\nX-Evil: yes.mp4");
        when(assetMedia.resolve(assetId)).thenReturn(unsafe);
        when(assetMedia.openStream(eq(unsafe), anyLong(), anyLong()))
                .thenReturn(new ByteArrayInputStream(MEDIA_BYTES));

        performStreaming(get("/api/assets/{assetId}/media", assetId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/octet-stream"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, not(containsString("\r"))))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, not(containsString("\n"))))
                .andExpect(header().doesNotExist("X-Evil"));
    }

    @Test
    void streamClosesInputOnSuccessAndOutputFailure() throws Exception {
        TrackingInputStream successfulInput = new TrackingInputStream(MEDIA_BYTES);
        when(assetMedia.openStream(descriptor, 0, MEDIA_BYTES.length)).thenReturn(successfulInput);
        controller.streamMedia(assetId, null).getBody().writeTo(OutputStream.nullOutputStream());
        assertThat(successfulInput.closed()).isTrue();

        TrackingInputStream failingInput = new TrackingInputStream(MEDIA_BYTES);
        when(assetMedia.openStream(descriptor, 0, MEDIA_BYTES.length)).thenReturn(failingInput);
        OutputStream failingOutput = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("client disconnected");
            }

            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                throw new IOException("client disconnected");
            }
        };

        assertThatThrownBy(() -> controller.streamMedia(assetId, null).getBody().writeTo(failingOutput))
                .isInstanceOf(IOException.class);
        assertThat(failingInput.closed()).isTrue();
    }

    @Test
    void transferUsesBoundedBuffersRatherThanReadingTheWholeObject() throws Exception {
        int size = 32_000;
        byte[] bytes = new byte[size];
        AssetMediaDescriptor large = descriptor(size, "video/mp4", "large.mp4");
        TrackingInputStream input = new TrackingInputStream(bytes);
        when(assetMedia.resolve(assetId)).thenReturn(large);
        when(assetMedia.openStream(large, 0, size)).thenReturn(input);

        controller.streamMedia(assetId, null).getBody().writeTo(OutputStream.nullOutputStream());

        assertThat(input.maximumRequestedRead()).isPositive().isLessThanOrEqualTo(8192);
        assertThat(input.closed()).isTrue();
        verify(assetMedia).openStream(large, 0, size);
    }

    private ResultActions performStreaming(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) throws Exception {
        MvcResult result = mockMvc.perform(requestBuilder)
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(result));
    }

    private AssetMediaDescriptor descriptor(long size, String contentType, String filename) {
        return new AssetMediaDescriptor(
                assetId == null ? UUID.randomUUID() : assetId,
                contentType,
                filename,
                size,
                new StoredObjectReference(
                        "workspace-media",
                        "users/owner/workspaces/workspace/assets/asset/raw/" + filename,
                        size,
                        contentType,
                        null
                )
        );
    }

    private static final class TrackingInputStream extends InputStream {

        private final ByteArrayInputStream delegate;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicInteger maximumRequestedRead = new AtomicInteger();

        private TrackingInputStream(byte[] bytes) {
            this.delegate = new ByteArrayInputStream(bytes);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            maximumRequestedRead.accumulateAndGet(length, Math::max);
            return delegate.read(bytes, offset, length);
        }

        @Override
        public void close() throws IOException {
            closed.set(true);
            delegate.close();
        }

        boolean closed() {
            return closed.get();
        }

        int maximumRequestedRead() {
            return maximumRequestedRead.get();
        }
    }
}
