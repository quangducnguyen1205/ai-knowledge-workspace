package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.search.application.TranscriptFingerprintRow;
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

    public String fingerprint(List<? extends TranscriptFingerprintRow> rows) {
        MessageDigest digest = sha256();
        updateInt(digest, rows.size());
        for (TranscriptFingerprintRow row : rows) {
            updateIntegerField(digest, row.segmentIndex());
            updateStringField(digest, row.text());
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

    private void updateInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }
}
