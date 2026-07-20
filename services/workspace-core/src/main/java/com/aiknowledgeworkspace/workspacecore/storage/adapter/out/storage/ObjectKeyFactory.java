package com.aiknowledgeworkspace.workspacecore.storage.adapter.out.storage;

import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ObjectKeyFactory {

    private static final int MAX_FILENAME_COMPONENT_LENGTH = 180;

    public String rawMediaKey(String userId, UUID workspaceId, UUID assetId, String originalFilename) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId is required for object key generation");
        }
        if (workspaceId == null) {
            throw new IllegalArgumentException("workspaceId is required for object key generation");
        }
        if (assetId == null) {
            throw new IllegalArgumentException("assetId is required for object key generation");
        }

        return "users/%s/workspaces/%s/assets/%s/raw/%s".formatted(
                safePathComponent(userId),
                workspaceId,
                assetId,
                safeFilename(originalFilename)
        );
    }

    String safeFilename(String originalFilename) {
        String filename = StringUtils.getFilename(originalFilename);
        if (!StringUtils.hasText(filename) || ".".equals(filename) || "..".equals(filename)) {
            return "upload.bin";
        }

        String sanitized = filename.trim()
                .replaceAll("\\s+", "-")
                .replaceAll("[^A-Za-z0-9._-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("-+\\.", ".")
                .replaceAll("^[.-]+", "")
                .replaceAll("[.-]+$", "");

        if (!StringUtils.hasText(sanitized)) {
            return "upload.bin";
        }
        if (sanitized.length() <= MAX_FILENAME_COMPONENT_LENGTH) {
            return sanitized;
        }

        int lastDotIndex = sanitized.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < sanitized.length() - 1) {
            String extension = sanitized.substring(lastDotIndex).toLowerCase(Locale.ROOT);
            int nameLength = MAX_FILENAME_COMPONENT_LENGTH - extension.length();
            if (nameLength > 0) {
                return sanitized.substring(0, nameLength) + extension;
            }
        }
        return sanitized.substring(0, MAX_FILENAME_COMPONENT_LENGTH);
    }

    private String safePathComponent(String value) {
        String sanitized = value.trim()
                .replaceAll("\\s+", "-")
                .replaceAll("[^A-Za-z0-9._-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^[.-]+", "")
                .replaceAll("[.-]+$", "");
        if (!StringUtils.hasText(sanitized)) {
            throw new IllegalArgumentException("Object key path component is empty after sanitization");
        }
        return sanitized;
    }
}
