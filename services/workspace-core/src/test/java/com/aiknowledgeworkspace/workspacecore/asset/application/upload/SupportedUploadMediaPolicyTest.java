package com.aiknowledgeworkspace.workspacecore.asset.application.upload;

import com.aiknowledgeworkspace.workspacecore.asset.InvalidUploadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportedUploadMediaPolicyTest {

    private final SupportedUploadMediaPolicy policy = new SupportedUploadMediaPolicy();

    @Test
    void rejectsTextFilesEvenWhenTheDeclaredMimeTypeIsText() {
        assertRejected(file("notes.txt", "text/plain", "notes".getBytes()));
    }

    @Test
    void rejectsAnMp4FilenameWhenItsContentIsNotAnIsoBaseMediaContainer() {
        assertRejected(file("lecture.mp4", "video/mp4", "not a video".getBytes()));
    }

    @Test
    void acceptsTheMp4QuickTimeFamilyAndNormalizesAnUppercaseExtension() {
        ValidatedUploadMedia validated = policy.validate(file("lecture.MP4", "video/mp4", mp4Signature()));

        assertThat(validated.originalFilename()).isEqualTo("lecture.MP4");
        assertThat(validated.contentType()).isEqualTo("video/mp4");
    }

    @Test
    void acceptsAValidWebmSignature() {
        assertThat(policy.validate(file("lecture.webm", "video/webm", webmSignature())).contentType())
                .isEqualTo("video/webm");
    }

    @Test
    void acceptsAValidAviSignature() {
        assertThat(policy.validate(file("lecture.avi", "video/x-msvideo", aviSignature())).contentType())
                .isEqualTo("video/x-msvideo");
    }

    @Test
    void rejectsAnUnsupportedExtensionEvenWhenItClaimsToBeVideo() {
        assertRejected(file("lecture.mkv", "video/webm", webmSignature()));
    }

    @Test
    void rejectsACompatibleExtensionWithAnIncompatibleSpecificMimeType() {
        assertRejected(file("lecture.mp4", "text/plain", mp4Signature()));
    }

    @Test
    void acceptsAValidSignatureWhenTheBrowserDoesNotProvideAMimeType() {
        assertThat(policy.validate(file("lecture.mp4", "", mp4Signature())).contentType())
                .isEqualTo("application/octet-stream");
    }

    @Test
    void acceptsAValidSignatureWithAGenericOctetStreamMimeType() {
        assertThat(policy.validate(file("lecture.mp4", "application/octet-stream", mp4Signature())).contentType())
                .isEqualTo("application/octet-stream");
    }

    @Test
    void rejectsFilesWithoutAnOriginalFilename() {
        assertRejected(file(null, "video/mp4", mp4Signature()));
    }

    private void assertRejected(MockMultipartFile file) {
        assertThatThrownBy(() -> policy.validate(file))
                .isInstanceOf(InvalidUploadRequestException.class)
                .hasMessage("Only MP4, MOV, M4V, WebM, and AVI video files are supported");
    }

    private MockMultipartFile file(String filename, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", filename, contentType, bytes);
    }

    private byte[] mp4Signature() {
        return new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
    }

    private byte[] webmSignature() {
        return new byte[] {(byte) 0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0, 0, 0};
    }

    private byte[] aviSignature() {
        return new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'A', 'V', 'I', ' '};
    }
}
