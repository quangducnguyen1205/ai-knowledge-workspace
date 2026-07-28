package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AssetMediaHttpRangeResolverTest {

    private final AssetMediaHttpRangeResolver resolver = new AssetMediaHttpRangeResolver();

    @Test
    void resolvesFullOpenEndedAndSuffixRanges() {
        assertThat(resolver.resolve(null, 10))
                .isEqualTo(new AssetMediaHttpRangeResolver.ResolvedMediaRange(0, 9, false));
        assertThat(resolver.resolve("bytes=0-0", 10))
                .isEqualTo(new AssetMediaHttpRangeResolver.ResolvedMediaRange(0, 0, true));
        assertThat(resolver.resolve("bytes=2-5", 10))
                .isEqualTo(new AssetMediaHttpRangeResolver.ResolvedMediaRange(2, 5, true));
        assertThat(resolver.resolve("bytes=6-", 10))
                .isEqualTo(new AssetMediaHttpRangeResolver.ResolvedMediaRange(6, 9, true));
        assertThat(resolver.resolve("bytes=-3", 10))
                .isEqualTo(new AssetMediaHttpRangeResolver.ResolvedMediaRange(7, 9, true));
        assertThat(resolver.resolve("bytes=0-999", 10))
                .isEqualTo(new AssetMediaHttpRangeResolver.ResolvedMediaRange(0, 9, true));
    }

    @Test
    void handlesOneByteObject() {
        assertThat(resolver.resolve(null, 1))
                .isEqualTo(new AssetMediaHttpRangeResolver.ResolvedMediaRange(0, 0, false));
        assertThat(resolver.resolve("bytes=0-0", 1))
                .isEqualTo(new AssetMediaHttpRangeResolver.ResolvedMediaRange(0, 0, true));
        assertThat(resolver.resolve("bytes=-1", 1))
                .isEqualTo(new AssetMediaHttpRangeResolver.ResolvedMediaRange(0, 0, true));
    }

    @Test
    void rejectsEmptyMalformedMultipleUnsatisfiableAndOverflowingRanges() {
        assertUnsatisfiable("", 10);
        assertUnsatisfiable("bytes=abc", 10);
        assertUnsatisfiable("items=0-1", 10);
        assertUnsatisfiable("bytes=0-0,2-2", 10);
        assertUnsatisfiable("bytes=10-", 10);
        assertUnsatisfiable("bytes=-0", 10);
        assertUnsatisfiable("bytes=9223372036854775807-", 10);
        assertUnsatisfiable("bytes=0-9223372036854775808", 10);
        assertUnsatisfiable("bytes=0-0", 0);
    }

    private void assertUnsatisfiable(String header, long totalSizeBytes) {
        assertThatThrownBy(() -> resolver.resolve(header, totalSizeBytes))
                .isInstanceOf(AssetMediaRangeNotSatisfiableException.class);
    }
}
