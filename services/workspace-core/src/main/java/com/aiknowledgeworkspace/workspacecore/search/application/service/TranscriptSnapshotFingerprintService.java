package com.aiknowledgeworkspace.workspacecore.search.application.service;

import com.aiknowledgeworkspace.workspacecore.search.application.model.TranscriptFingerprintRow;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TranscriptSnapshotFingerprintService {

    private static final byte FIELD_SEPARATOR = 0x1f;
    private static final byte TIMING_MARKER = 0x1e;

    public String fingerprint(List<? extends TranscriptFingerprintRow> rows) {
        MessageDigest digest = sha256();
        updateInt(digest, rows.size());
        for (TranscriptFingerprintRow row : rows) {
            updateIntegerField(digest, row.segmentIndex());
            updateStringField(digest, row.text());
            updateTimingIfPresent(digest, row.startMs(), row.endMs());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }

    private void updateIntegerField(MessageDigest digest, Integer value) {
        digest.update(FIELD_SEPARATOR);
        if (value == null) {
            updateInt(digest, -1);
            return;
        }
        updateInt(digest, 4);
        updateInt(digest, value);
    }

    private void updateStringField(MessageDigest digest, String value) {
        digest.update(FIELD_SEPARATOR);
        if (value == null) {
            updateInt(digest, -1);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private void updateTimingIfPresent(MessageDigest digest, Long startMs, Long endMs) {
        if (startMs == null && endMs == null) {
            return;
        }
        digest.update(TIMING_MARKER);
        updateLongField(digest, startMs);
        updateLongField(digest, endMs);
    }

    private void updateLongField(MessageDigest digest, Long value) {
        digest.update(FIELD_SEPARATOR);
        if (value == null) {
            updateInt(digest, -1);
            return;
        }
        updateInt(digest, Long.BYTES);
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }

    private void updateInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }
}
