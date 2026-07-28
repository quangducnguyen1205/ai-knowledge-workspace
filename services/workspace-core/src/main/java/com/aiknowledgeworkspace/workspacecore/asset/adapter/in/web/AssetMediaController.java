package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetMediaDescriptor;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.AssetMediaUseCase;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/assets/{assetId}/media")
public class AssetMediaController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssetMediaController.class);
    private static final int STREAM_BUFFER_SIZE = 8192;
    private static final String PRIVATE_NO_STORE = "private, no-store";

    private final AssetMediaUseCase assetMedia;
    private final AssetMediaHttpRangeResolver rangeResolver = new AssetMediaHttpRangeResolver();

    public AssetMediaController(AssetMediaUseCase assetMedia) {
        this.assetMedia = assetMedia;
    }

    @GetMapping
    public ResponseEntity<StreamingResponseBody> streamMedia(
            @PathVariable UUID assetId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader
    ) {
        AssetMediaDescriptor descriptor = assetMedia.resolve(assetId);
        AssetMediaHttpRangeResolver.ResolvedMediaRange range =
                rangeResolver.resolve(rangeHeader, descriptor.totalSizeBytes());
        HttpStatus status = range.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK;
        HttpHeaders headers = mediaHeaders(descriptor, range);
        InputStream inputStream = assetMedia.openStream(descriptor, range.start(), range.length());
        StreamingResponseBody responseBody = outputStream ->
                transfer(assetId, inputStream, outputStream, range);

        LOGGER.info(
                "Authorized asset media response assetId={} status={} mode={} rangeStart={} rangeEnd={}",
                assetId,
                status.value(),
                range.partial() ? "partial" : "full",
                range.start(),
                range.end()
        );
        return ResponseEntity.status(status).headers(headers).body(responseBody);
    }

    @RequestMapping(method = RequestMethod.HEAD)
    public ResponseEntity<Void> headMedia(@PathVariable UUID assetId) {
        AssetMediaDescriptor descriptor = assetMedia.resolve(assetId);
        AssetMediaHttpRangeResolver.ResolvedMediaRange fullRange =
                rangeResolver.resolve(null, descriptor.totalSizeBytes());
        LOGGER.info("Authorized asset media response assetId={} status=200 mode=head", assetId);
        return ResponseEntity.ok()
                .headers(mediaHeaders(descriptor, fullRange))
                .build();
    }

    private HttpHeaders mediaHeaders(
            AssetMediaDescriptor descriptor,
            AssetMediaHttpRangeResolver.ResolvedMediaRange range
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(safeContentType(descriptor.contentType()));
        headers.setContentLength(range.length());
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.set(HttpHeaders.CACHE_CONTROL, PRIVATE_NO_STORE);
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(safeFilename(descriptor.originalFilename()), StandardCharsets.UTF_8)
                .build());
        if (range.partial()) {
            headers.set(
                    HttpHeaders.CONTENT_RANGE,
                    "bytes " + range.start() + "-" + range.end() + "/" + descriptor.totalSizeBytes()
            );
        }
        return headers;
    }

    private MediaType safeContentType(String contentType) {
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            return mediaType.isWildcardType() || mediaType.isWildcardSubtype()
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : mediaType;
        } catch (RuntimeException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String safeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "media";
        }
        StringBuilder safe = new StringBuilder(Math.min(originalFilename.length(), 255));
        for (int index = 0; index < originalFilename.length() && safe.length() < 255; index++) {
            char character = originalFilename.charAt(index);
            if (character < 0x20 || character == 0x7f || character == '/' || character == '\\') {
                safe.append('_');
            } else {
                safe.append(character);
            }
        }
        return safe.toString();
    }

    private void transfer(
            UUID assetId,
            InputStream inputStream,
            java.io.OutputStream outputStream,
            AssetMediaHttpRangeResolver.ResolvedMediaRange range
    ) throws IOException {
        try (inputStream) {
            byte[] buffer = new byte[STREAM_BUFFER_SIZE];
            long remaining = range.length();
            while (remaining > 0) {
                int read = inputStream.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    throw new IOException("Media stream ended before the requested range completed");
                }
                if (read == 0) {
                    int singleByte = inputStream.read();
                    if (singleByte < 0) {
                        throw new IOException("Media stream ended before the requested range completed");
                    }
                    outputStream.write(singleByte);
                    remaining--;
                    continue;
                }
                outputStream.write(buffer, 0, read);
                remaining -= read;
            }
        } catch (IOException exception) {
            LOGGER.warn(
                    "Asset media transfer failed assetId={} rangeStart={} rangeEnd={} exceptionType={}",
                    assetId,
                    range.start(),
                    range.end(),
                    exception.getClass().getSimpleName()
            );
            throw exception;
        }
    }
}
