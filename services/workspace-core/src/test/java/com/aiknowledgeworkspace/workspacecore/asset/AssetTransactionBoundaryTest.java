package com.aiknowledgeworkspace.workspacecore.asset;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobView;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

class AssetTransactionBoundaryTest {

    @Test
    void uploadOrchestrationRemainsOutsideThePersistenceTransaction() throws Exception {
        Method upload = UploadAssetApplicationService.class.getMethod(
                "uploadAsset", UUID.class, MultipartFile.class, String.class
        );
        Method persistKafka = AssetPersistenceService.class.getMethod(
                "persistKafkaRequestUpload",
                UUID.class,
                String.class,
                String.class,
                com.aiknowledgeworkspace.workspacecore.workspace.Workspace.class,
                com.aiknowledgeworkspace.workspacecore.storage.StoredObject.class
        );

        assertThat(transactional(upload)).isNull();
        assertThat(transactional(persistKafka)).isNotNull();
    }

    @Test
    void controllerTranscriptFallbackRemainsOutsideAnEnclosingTransaction() throws Exception {
        Method read = AssetQueryApplicationService.class.getMethod("getAssetTranscript", UUID.class);
        Method compatibilityFallback = DirectProcessingCompatibilityAdapter.class.getDeclaredMethod(
                "loadOrCaptureTranscript", Asset.class, ProcessingJobView.class
        );

        assertThat(transactional(read)).isNull();
        assertThat(transactional(compatibilityFallback)).isNull();
    }

    @Test
    void indexingFallbackAndCanonicalReplacementRemainTransactional() throws Exception {
        Method indexingFallback = DirectProcessingCompatibilityAdapter.class.getMethod(
                "loadAuthorizedIndexingSourceForCompletedProcessing", UUID.class, String.class
        );
        Method replace = AssetTranscriptSnapshotService.class.getMethod(
                "replaceCanonicalSnapshot", Asset.class, java.util.List.class
        );

        assertThat(transactional(indexingFallback)).isNotNull();
        assertThat(transactional(replace)).isNotNull();
    }

    private Transactional transactional(Method method) {
        return AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
    }
}
