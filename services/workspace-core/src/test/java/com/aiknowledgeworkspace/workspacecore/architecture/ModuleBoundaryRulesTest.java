package com.aiknowledgeworkspace.workspacecore.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.processing.integration.request.ProcessingRequestedEventCodec;
import com.aiknowledgeworkspace.workspacecore.search.integration.request.IndexingRequestedEventCodec;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.util.List;

class ModuleBoundaryRulesTest {

    private static final JavaClasses WORKSPACE_CORE_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.aiknowledgeworkspace.workspacecore");

    @Test
    void commonWebDoesNotDependOnFeatureOrIntegrationPackages() {
        noClasses()
                .that().resideInAPackage("..common.web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..asset..",
                        "..assistant..",
                        "..integration..",
                        "..processing..",
                        "..search..",
                        "..storage..",
                        "..workspace.."
                )
                .check(WORKSPACE_CORE_CLASSES);
    }

    @Test
    void outboxDoesNotDependOnProductFeatureImplementations() {
        noClasses()
                .that().resideInAPackage("..outbox..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..asset..",
                        "..processing..",
                        "..search..",
                        "..storage..",
                        "..workspace.."
                )
                .check(WORKSPACE_CORE_CLASSES);
    }

    @Test
    void eventCodecsRemainOwnedByTheirFeatureModules() {
        assertThat(ProcessingRequestedEventCodec.class.getPackageName())
                .isEqualTo("com.aiknowledgeworkspace.workspacecore.processing.integration.request");
        assertThat(IndexingRequestedEventCodec.class.getPackageName())
                .isEqualTo("com.aiknowledgeworkspace.workspacecore.search.integration.request");
    }

    @Test
    void processingSearchAndWorkspaceDoNotDependOnAssetImplementations() {
        noClasses()
                .that().resideInAnyPackage("..processing..", "..search..", "..workspace..")
                .should().dependOnClassesThat().resideInAPackage("..asset..")
                .check(WORKSPACE_CORE_CLASSES);
    }

    @Test
    void assetUsesOnlyProcessingAndSearchApplicationBoundaries() {
        List<Dependency> forbiddenDependencies = WORKSPACE_CORE_CLASSES.stream()
                .filter(javaClass -> javaClass.getPackageName().startsWith(
                        "com.aiknowledgeworkspace.workspacecore.asset"))
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> isProcessingOrSearch(dependency.getTargetClass().getPackageName()))
                .filter(dependency -> !isExposedApplicationType(dependency.getTargetClass().getPackageName()))
                .filter(dependency -> !dependency.getTargetClass().getFullName().equals(
                        "com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus"))
                .toList();

        assertThat(forbiddenDependencies)
                .as("asset may use processing/search application APIs, but not their entities, repositories, or services")
                .isEmpty();
    }

    @Test
    void canonicalTranscriptServicesDoNotDependOnFastApiContracts() {
        noClasses()
                .that().haveSimpleName("AssetTranscriptSnapshotService")
                .or().haveSimpleName("AssetTranscriptQueryService")
                .should().dependOnClassesThat().resideInAPackage("..integration.fastapi..")
                .check(WORKSPACE_CORE_CLASSES);
    }

    @Test
    void assetControllerDoesNotDependOnPersistenceRepositories() {
        noClasses()
                .that().haveSimpleName("AssetController")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .check(WORKSPACE_CORE_CLASSES);
    }

    @Test
    void directProcessingCompatibilityAdapterRemainsInternal() throws ClassNotFoundException {
        Class<?> adapter = Class.forName(
                "com.aiknowledgeworkspace.workspacecore.asset.DirectProcessingCompatibilityAdapter"
        );
        assertThat(Modifier.isPublic(adapter.getModifiers())).isFalse();
    }

    private static boolean isProcessingOrSearch(String packageName) {
        return packageName.startsWith("com.aiknowledgeworkspace.workspacecore.processing")
                || packageName.startsWith("com.aiknowledgeworkspace.workspacecore.search");
    }

    private static boolean isExposedApplicationType(String packageName) {
        return packageName.equals("com.aiknowledgeworkspace.workspacecore.processing.application")
                || packageName.equals("com.aiknowledgeworkspace.workspacecore.search.application");
    }

}
