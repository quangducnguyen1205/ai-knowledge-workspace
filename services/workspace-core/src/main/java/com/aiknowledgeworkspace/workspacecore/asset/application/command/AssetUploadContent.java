package com.aiknowledgeworkspace.workspacecore.asset.application.command;

import java.io.IOException;
import java.io.InputStream;

@FunctionalInterface
public interface AssetUploadContent {

    InputStream openStream() throws IOException;
}
