package com.aiknowledgeworkspace.workspacecore.storage;

import com.aiknowledgeworkspace.workspacecore.storage.adapter.out.storage.ObjectKeyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObjectKeyFactoryTest {

    private final ObjectKeyFactory objectKeyFactory = new ObjectKeyFactory();

    @Test
    void rawMediaKeyIncludesUserWorkspaceAssetAndSanitizedFilename() {
        UUID workspaceId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID assetId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        String key = objectKeyFactory.rawMediaKey(
                "student@example.com",
                workspaceId,
                assetId,
                "../Lecture 01: Intro?.mp4"
        );

        assertThat(key).isEqualTo(
                "users/student-example.com/workspaces/11111111-1111-1111-1111-111111111111/"
                        + "assets/22222222-2222-2222-2222-222222222222/raw/Lecture-01-Intro.mp4"
        );
    }

    @Test
    void rawMediaKeyFallsBackToSafeFilenameWhenOriginalFilenameIsNotUsable() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        String key = objectKeyFactory.rawMediaKey("user-1", workspaceId, assetId, "../..");

        assertThat(key).endsWith("/raw/upload.bin");
    }

    @Test
    void rawMediaKeySanitizesUnsafeUserIdPathComponent() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        String key = objectKeyFactory.rawMediaKey("../user@example.com", workspaceId, assetId, "lecture.mp4");

        assertThat(key)
                .startsWith("users/user-example.com/workspaces/" + workspaceId + "/assets/" + assetId + "/raw/");
        assertThat(key).doesNotContain("..");
    }

    @Test
    void rawMediaKeyRejectsMissingOwnershipInputs() {
        assertThatThrownBy(() -> objectKeyFactory.rawMediaKey(" ", UUID.randomUUID(), UUID.randomUUID(), "a.mp4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId is required");
    }
}
