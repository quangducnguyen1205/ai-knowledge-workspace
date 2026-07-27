package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import com.aiknowledgeworkspace.workspacecore.asset.application.exception.InvalidYouTubeUrlException;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.NormalizedYouTubeUrl;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class YouTubeUrlPolicy {

    private static final Set<String> YOUTUBE_HOSTS = Set.of(
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com"
    );
    private static final String SHORT_HOST = "youtu.be";
    private static final Pattern PUBLIC_VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern PERSISTED_VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final String CANONICAL_URL_PREFIX = "https://www.youtube.com/watch?v=";

    public NormalizedYouTubeUrl normalize(String submittedUrl) {
        if (!StringUtils.hasText(submittedUrl)) {
            throw invalid();
        }

        URI uri = parse(submittedUrl.trim());
        validateAuthority(uri);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String videoId = SHORT_HOST.equals(host)
                ? videoIdFromShortUrl(uri)
                : videoIdFromYoutubeUrl(uri, host);
        validateVideoId(videoId);
        return new NormalizedYouTubeUrl(videoId, canonicalUrl(videoId));
    }

    public static String canonicalUrl(String youtubeVideoId) {
        if (youtubeVideoId == null) {
            return null;
        }
        if (!PERSISTED_VIDEO_ID.matcher(youtubeVideoId).matches()) {
            throw new IllegalArgumentException("youtubeVideoId is not canonical");
        }
        return CANONICAL_URL_PREFIX + youtubeVideoId;
    }

    private URI parse(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException exception) {
            throw invalid();
        }
    }

    private void validateAuthority(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getPort() != -1) {
            throw invalid();
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!YOUTUBE_HOSTS.contains(host) && !SHORT_HOST.equals(host)) {
            throw invalid();
        }
    }

    private String videoIdFromShortUrl(URI uri) {
        String path = uri.getRawPath();
        if (path == null || !path.matches("/[A-Za-z0-9_-]{11}")) {
            throw invalid();
        }
        return path.substring(1);
    }

    private String videoIdFromYoutubeUrl(URI uri, String host) {
        String path = uri.getRawPath();
        if ("/watch".equals(path)) {
            return singleWatchVideoId(uri.getRawQuery());
        }
        if ("www.youtube.com".equals(host)
                && path != null
                && path.matches("/shorts/[A-Za-z0-9_-]{11}")) {
            return path.substring("/shorts/".length());
        }
        throw invalid();
    }

    private String singleWatchVideoId(String rawQuery) {
        if (rawQuery == null) {
            throw invalid();
        }
        List<String> videoIds = new ArrayList<>();
        for (String parameter : rawQuery.split("&", -1)) {
            int separator = parameter.indexOf('=');
            String rawName = separator < 0 ? parameter : parameter.substring(0, separator);
            String rawValue = separator < 0 ? "" : parameter.substring(separator + 1);
            String name = decode(rawName);
            if ("v".equals(name)) {
                videoIds.add(decode(rawValue));
            }
        }
        if (videoIds.size() != 1 || !StringUtils.hasText(videoIds.get(0))) {
            throw invalid();
        }
        return videoIds.get(0);
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private void validateVideoId(String videoId) {
        if (!PUBLIC_VIDEO_ID.matcher(videoId).matches()) {
            throw invalid();
        }
    }

    private InvalidYouTubeUrlException invalid() {
        return new InvalidYouTubeUrlException("A supported public YouTube HTTPS URL is required");
    }
}
