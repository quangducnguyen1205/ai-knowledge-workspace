package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetCommandApplicationService;
import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetTranscriptSnapshotService;
import com.aiknowledgeworkspace.workspacecore.asset.application.command.AssetUploadCommand;
import com.aiknowledgeworkspace.workspacecore.asset.application.service.UploadAssetApplicationService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

class AssetTransactionBoundaryTest {

    @Test
    void externalUploadAndDeleteOrchestrationRemainOutsideDatabaseTransactions() throws Exception {
        Method upload = UploadAssetApplicationService.class.getMethod("upload", AssetUploadCommand.class);
        Method delete = AssetCommandApplicationService.class.getMethod("delete", java.util.UUID.class);

        assertThat(transactional(upload)).isNull();
        assertThat(transactional(delete)).isNull();
    }

    @Test
    void uploadProductTruthAndOutboxIntentShareOneTransaction() throws Exception {
        Class<?> transactionClass = Class.forName(
                "com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetUploadTransaction"
        );
        Method persist = transactionClass.getDeclaredMethod(
                "persist",
                java.util.UUID.class,
                String.class,
                String.class,
                java.util.UUID.class,
                String.class,
                com.aiknowledgeworkspace.workspacecore.storage.api.StoredObjectReference.class
        );

        assertThat(transactional(persist)).isNotNull();
    }

    @Test
    void canonicalReplacementAndDatabaseMutationsAreExplicitTransactions() throws Exception {
        Method replace = AssetTranscriptSnapshotService.class.getMethod(
                "replaceCanonicalSnapshot", Asset.class, java.util.List.class
        );
        Class<?> mutationClass = Class.forName(
                "com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetMutationTransaction"
        );
        Method updateTitle = mutationClass.getDeclaredMethod("updateTitle", Asset.class, String.class);
        Method delete = mutationClass.getDeclaredMethod("delete", Asset.class);

        assertThat(transactional(replace)).isNotNull();
        assertThat(transactional(updateTitle)).isNotNull();
        assertThat(transactional(delete)).isNotNull();
    }

    private Transactional transactional(Method method) {
        return AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
    }
}
