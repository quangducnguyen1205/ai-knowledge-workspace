package com.aiknowledgeworkspace.workspacecore.asset.application.compatibility;

import org.springframework.core.io.Resource;

public record DirectProcessingUploadCommand(Resource resource, String originalFilename, String title) {
}
