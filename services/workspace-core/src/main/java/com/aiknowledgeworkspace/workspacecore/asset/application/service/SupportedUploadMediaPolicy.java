package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import com.aiknowledgeworkspace.workspacecore.asset.application.command.AssetUploadCommand;

import com.aiknowledgeworkspace.workspacecore.asset.application.command.AssetUploadContent;

import com.aiknowledgeworkspace.workspacecore.asset.application.exception.InvalidUploadRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SupportedUploadMediaPolicy {

    private static final int SIGNATURE_LENGTH = 12;
    private static final String GENERIC_CONTENT_TYPE = "application/octet-stream";
    private static final String SUPPORTED_FORMATS_MESSAGE =
            "Only MP4, MOV, M4V, WebM, and AVI video files are supported";

    ValidatedUploadMedia validate(AssetUploadCommand command) {
        String originalFilename = normalizeOriginalFilename(command.originalFilename());
        MediaFamily mediaFamily = MediaFamily.fromFilename(originalFilename);
        byte[] signature = readSignature(command.content());

        if (!mediaFamily.matches(signature)) {
            throw unsupportedMedia();
        }

        String contentType = normalizeContentType(command.contentType());
        if (!isGenericContentType(contentType) && !mediaFamily.supportedContentTypes().contains(contentType)) {
            throw unsupportedMedia();
        }

        return new ValidatedUploadMedia(originalFilename, contentType);
    }

    private String normalizeOriginalFilename(String originalFilename) {
        String cleanedFilename = StringUtils.getFilename(originalFilename);
        if (!StringUtils.hasText(cleanedFilename)) {
            throw unsupportedMedia();
        }

        int maxLength = 255;
        if (cleanedFilename.length() <= maxLength) {
            return cleanedFilename;
        }

        int lastDotIndex = cleanedFilename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < cleanedFilename.length() - 1) {
            String extension = cleanedFilename.substring(lastDotIndex);
            int truncatedLength = maxLength - extension.length();
            if (truncatedLength > 0) {
                return cleanedFilename.substring(0, truncatedLength) + extension;
            }
        }
        return cleanedFilename.substring(0, maxLength);
    }

    private byte[] readSignature(AssetUploadContent content) {
        try (InputStream inputStream = content.openStream()) {
            return inputStream.readNBytes(SIGNATURE_LENGTH);
        } catch (IOException exception) {
            throw new InvalidUploadRequestException("Uploaded file could not be read");
        }
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return GENERIC_CONTENT_TYPE;
        }
        int parameterIndex = contentType.indexOf(';');
        String mediaType = parameterIndex >= 0 ? contentType.substring(0, parameterIndex) : contentType;
        return mediaType.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isGenericContentType(String contentType) {
        return GENERIC_CONTENT_TYPE.equals(contentType) || "video/*".equals(contentType);
    }

    private InvalidUploadRequestException unsupportedMedia() {
        return new InvalidUploadRequestException(SUPPORTED_FORMATS_MESSAGE);
    }

    private enum MediaFamily {
        MP4_QUICKTIME(
                Set.of("mp4", "mov", "m4v"),
                Set.of("video/mp4", "video/quicktime", "video/x-m4v")
        ) {
            @Override
            boolean matches(byte[] signature) {
                return signature.length >= 8
                        && signature[4] == 'f'
                        && signature[5] == 't'
                        && signature[6] == 'y'
                        && signature[7] == 'p';
            }
        },
        WEBM(
                Set.of("webm"),
                Set.of("video/webm", "application/webm")
        ) {
            @Override
            boolean matches(byte[] signature) {
                return signature.length >= 4
                        && signature[0] == 0x1A
                        && signature[1] == 0x45
                        && signature[2] == (byte) 0xDF
                        && signature[3] == (byte) 0xA3;
            }
        },
        AVI(
                Set.of("avi"),
                Set.of("video/x-msvideo", "video/avi", "video/msvideo")
        ) {
            @Override
            boolean matches(byte[] signature) {
                return signature.length >= 12
                        && signature[0] == 'R'
                        && signature[1] == 'I'
                        && signature[2] == 'F'
                        && signature[3] == 'F'
                        && signature[8] == 'A'
                        && signature[9] == 'V'
                        && signature[10] == 'I'
                        && signature[11] == ' ';
            }
        };

        private final Set<String> extensions;
        private final Set<String> supportedContentTypes;

        MediaFamily(Set<String> extensions, Set<String> supportedContentTypes) {
            this.extensions = extensions;
            this.supportedContentTypes = supportedContentTypes;
        }

        static MediaFamily fromFilename(String originalFilename) {
            int lastDotIndex = originalFilename.lastIndexOf('.');
            if (lastDotIndex <= 0 || lastDotIndex == originalFilename.length() - 1) {
                throw new InvalidUploadRequestException(SUPPORTED_FORMATS_MESSAGE);
            }

            String extension = originalFilename.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT);
            for (MediaFamily mediaFamily : values()) {
                if (mediaFamily.extensions.contains(extension)) {
                    return mediaFamily;
                }
            }
            throw new InvalidUploadRequestException(SUPPORTED_FORMATS_MESSAGE);
        }

        abstract boolean matches(byte[] signature);

        Set<String> supportedContentTypes() {
            return supportedContentTypes;
        }
    }
}
