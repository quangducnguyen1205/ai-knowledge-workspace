package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

import java.util.List;
import org.springframework.http.HttpRange;

class AssetMediaHttpRangeResolver {

    ResolvedMediaRange resolve(String rangeHeader, long totalSizeBytes) {
        if (totalSizeBytes <= 0) {
            throw new AssetMediaRangeNotSatisfiableException(totalSizeBytes);
        }
        if (rangeHeader == null) {
            return new ResolvedMediaRange(0, totalSizeBytes - 1, false);
        }
        if (rangeHeader.isBlank()) {
            throw new AssetMediaRangeNotSatisfiableException(totalSizeBytes);
        }

        final List<HttpRange> ranges;
        try {
            ranges = HttpRange.parseRanges(rangeHeader);
        } catch (IllegalArgumentException exception) {
            throw new AssetMediaRangeNotSatisfiableException(totalSizeBytes);
        }
        if (ranges.size() != 1) {
            throw new AssetMediaRangeNotSatisfiableException(totalSizeBytes);
        }

        try {
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(totalSizeBytes);
            long end = range.getRangeEnd(totalSizeBytes);
            if (start < 0 || start >= totalSizeBytes || end < start || end >= totalSizeBytes) {
                throw new AssetMediaRangeNotSatisfiableException(totalSizeBytes);
            }
            Math.addExact(Math.subtractExact(end, start), 1);
            return new ResolvedMediaRange(start, end, true);
        } catch (AssetMediaRangeNotSatisfiableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AssetMediaRangeNotSatisfiableException(totalSizeBytes);
        }
    }

    record ResolvedMediaRange(long start, long end, boolean partial) {

        long length() {
            return end - start + 1;
        }
    }
}
