package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

import java.math.BigDecimal;

/**
 * {@code positionMs} is bound as an exact number so the application layer can reject a
 * non-integer value rather than let JSON coercion truncate it.
 */
public record SaveAssetPlaybackProgressRequest(BigDecimal positionMs, Boolean completed) {
}
