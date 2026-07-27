package com.aiknowledgeworkspace.workspacecore.asset.application.youtube;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiknowledgeworkspace.workspacecore.asset.application.exception.InvalidYouTubeUrlException;
import com.aiknowledgeworkspace.workspacecore.asset.application.service.YouTubeUrlPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class YouTubeUrlPolicyTest {

    private static final String VIDEO_ID = "abc_DEF-123";
    private final YouTubeUrlPolicy policy = new YouTubeUrlPolicy();

    @Test
    void acceptsEverySupportedFormAndDerivesOneCanonicalUrl() {
        List<String> accepted = List.of(
                "https://www.youtube.com/watch?v=" + VIDEO_ID,
                "https://youtube.com/watch?v=" + VIDEO_ID,
                "https://m.youtube.com/watch?v=" + VIDEO_ID,
                "https://youtu.be/" + VIDEO_ID,
                "https://www.youtube.com/shorts/" + VIDEO_ID
        );

        assertThat(accepted)
                .allSatisfy(url -> assertThat(policy.normalize(url))
                        .satisfies(result -> {
                            assertThat(result.youtubeVideoId()).isEqualTo(VIDEO_ID);
                            assertThat(result.canonicalUrl())
                                    .isEqualTo("https://www.youtube.com/watch?v=" + VIDEO_ID);
                        }));
    }

    @Test
    void ignoresNavigationTrackingFragmentAndPlaylistContext() {
        var watch = policy.normalize(
                "https://www.youtube.com/watch?utm_source=test&v=%s&list=PL123&t=42&start=10#chapter"
                        .formatted(VIDEO_ID)
        );
        var shortUrl = policy.normalize("https://youtu.be/%s?si=tracking&t=42#chapter".formatted(VIDEO_ID));

        assertThat(watch.youtubeVideoId()).isEqualTo(VIDEO_ID);
        assertThat(shortUrl.youtubeVideoId()).isEqualTo(VIDEO_ID);
        assertThat(watch.canonicalUrl()).isEqualTo(shortUrl.canonicalUrl());
    }

    @Test
    void rejectsSchemesAuthoritiesAndHostLookalikesOutsideTheExactAllowlist() {
        List<String> rejected = List.of(
                "http://www.youtube.com/watch?v=" + VIDEO_ID,
                "https://youtube.com.evil.example/watch?v=" + VIDEO_ID,
                "https://evil-youtube.com/watch?v=" + VIDEO_ID,
                "https://user@youtube.com/watch?v=" + VIDEO_ID,
                "https://youtube.com:443/watch?v=" + VIDEO_ID,
                "https://127.0.0.1/watch?v=" + VIDEO_ID,
                "https://[::1]/watch?v=" + VIDEO_ID
        );

        assertThat(rejected).allSatisfy(this::assertInvalid);
    }

    @Test
    void rejectsUnsupportedYoutubeResourcesAndPlaylistOnlyUrls() {
        List<String> rejected = List.of(
                "https://youtube.com/playlist?list=PL123",
                "https://youtube.com/embed/" + VIDEO_ID,
                "https://youtube.com/channel/UC123",
                "https://youtube.com/@example",
                "https://youtube.com/results?search_query=test",
                "https://youtube.com/live/" + VIDEO_ID,
                "https://youtube.com/shorts/" + VIDEO_ID,
                "https://m.youtube.com/shorts/" + VIDEO_ID,
                "https://youtube.com/watch/",
                "https://youtu.be/" + VIDEO_ID + "/extra"
        );

        assertThat(rejected).allSatisfy(this::assertInvalid);
    }

    @Test
    void rejectsMissingDuplicateBlankMalformedOrConflictingWatchIds() {
        List<String> rejected = List.of(
                "https://youtube.com/watch",
                "https://youtube.com/watch?v=",
                "https://youtube.com/watch?v=%20",
                "https://youtube.com/watch?v=%ZZ",
                "https://youtube.com/watch?v=%s&v=%s".formatted(VIDEO_ID, VIDEO_ID),
                "https://youtube.com/watch?v=%s&v=zyx_WVU-987".formatted(VIDEO_ID)
        );

        assertThat(rejected).allSatisfy(this::assertInvalid);
    }

    @Test
    void rejectsInvalidVideoIdLengthAndCharacters() {
        List<String> rejected = List.of(
                "https://youtube.com/watch?v=short",
                "https://youtube.com/watch?v=abc.DEF-123",
                "https://youtu.be/abc%5FDEF-123",
                "https://youtube.com/shorts/abc_DEF-1234"
        );

        assertThat(rejected).allSatisfy(this::assertInvalid);
    }

    @Test
    void rejectsBlankAndMalformedInputWithoutNetworkAccess() {
        assertInvalid(null);
        assertInvalid(" ");
        assertInvalid("https://youtube.com/watch?v=" + VIDEO_ID + "%");
    }

    private void assertInvalid(String url) {
        assertThatThrownBy(() -> policy.normalize(url))
                .isInstanceOf(InvalidYouTubeUrlException.class)
                .hasMessage("A supported public YouTube HTTPS URL is required");
    }
}
