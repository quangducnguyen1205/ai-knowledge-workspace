package com.aiknowledgeworkspace.workspacecore.asset.application.command;

import java.math.BigDecimal;

/**
 * Unvalidated playback-progress input. {@code positionMs} keeps the exact submitted number so the
 * application layer can reject a non-integer millisecond value instead of silently truncating it.
 */
public record SaveAssetPlaybackProgressCommand(BigDecimal positionMs, Boolean completed) {
}
