package com.aiknowledgeworkspace.workspacecore.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;

class ModuleBoundaryRulesTest {

    private static final String ROOT = "com.aiknowledgeworkspace.workspacecore.";
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.aiknowledgeworkspace.workspacecore");

    @Test
    void applicationLayersDoNotDependOnInfrastructureImplementations() {
        noClasses()
                .that().resideInAnyPackage("..application..", "..relay..", "..operator..", "..recovery..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .check(CLASSES);
    }

    @Test
    void applicationLayersDoNotLeakSpringDataOrWebTransportTypes() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.data..",
                        "org.springframework.web..",
                        "org.springframework.http..",
                        "org.springframework.web.multipart.."
                )
                .check(CLASSES);
    }

    @Test
    void inboundAdaptersDoNotAccessPersistenceRepositories() {
        List<Dependency> repositoryDependencies = CLASSES.stream()
                .filter(javaClass -> javaClass.getSimpleName().endsWith("Controller")
                        || javaClass.getSimpleName().endsWith("Listener"))
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> dependency.getTargetClass().isAssignableTo(Repository.class))
                .toList();

        assertThat(repositoryDependencies)
                .as("controllers and listeners must enter through application contracts")
                .isEmpty();
    }

    @Test
    void controllersDoNotDependOnJpaEntities() {
        List<Dependency> entityDependencies = CLASSES.stream()
                .filter(javaClass -> javaClass.getSimpleName().endsWith("Controller"))
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> dependency.getTargetClass().isAnnotatedWith(Entity.class))
                .toList();

        assertThat(entityDependencies)
                .as("controllers must expose web models rather than JPA entities")
                .isEmpty();
    }

    @Test
    void springDataRepositoriesArePackagePrivateAdapterDetails() {
        List<String> publicRepositories = CLASSES.stream()
                .filter(javaClass -> javaClass.isAssignableTo(Repository.class))
                .filter(javaClass -> javaClass.getModifiers().contains(JavaModifier.PUBLIC))
                .map(javaClass -> javaClass.getFullName())
                .toList();

        assertThat(publicRepositories)
                .as("Spring Data repositories must be hidden behind application-owned stores")
                .isEmpty();
    }

    @Test
    void repositoriesAndJpaEntitiesDoNotCrossModuleBoundaries() {
        List<Dependency> forbiddenDependencies = CLASSES.stream()
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> isProductType(dependency.getOriginClass().getPackageName()))
                .filter(dependency -> isProductType(dependency.getTargetClass().getPackageName()))
                .filter(dependency -> !moduleName(dependency.getOriginClass().getPackageName())
                        .equals(moduleName(dependency.getTargetClass().getPackageName())))
                .filter(dependency -> dependency.getTargetClass().getSimpleName().endsWith("Repository")
                        || dependency.getTargetClass().isAnnotatedWith(Entity.class))
                .toList();

        assertThat(forbiddenDependencies)
                .as("repositories and JPA entities are private implementation details of their owning module")
                .isEmpty();
    }

    @Test
    void commonWebAndOutboxRemainProductFeatureNeutral() {
        noClasses()
                .that().resideInAnyPackage("..common.web..", "..outbox..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..asset..",
                        "..assistant..",
                        "..processing..",
                        "..search..",
                        "..storage..",
                        "..workspace.."
                )
                .check(CLASSES);
    }

    @Test
    void productModulesDoNotDependOnFastApiProcessingWireTypes() {
        noClasses()
                .that().resideOutsideOfPackage("..integration.fastapi.processing..")
                .should().dependOnClassesThat().resideInAPackage("..integration.fastapi.processing..")
                .check(CLASSES);
    }

    @Test
    void nonSearchModulesDoNotDependOnElasticsearchInfrastructure() {
        noClasses()
                .that().resideOutsideOfPackage("..search..")
                .should().dependOnClassesThat().resideInAPackage("..search.infrastructure.elasticsearch..")
                .check(CLASSES);
    }

    @Test
    void indexingTransactionDoesNotPerformExternalIndexCalls() {
        noClasses()
                .that().haveSimpleName("IndexingAttemptTransactionService")
                .should().dependOnClassesThat().haveSimpleName("TranscriptIndexWriter")
                .orShould().dependOnClassesThat().haveSimpleName("TranscriptSearchIndexClient")
                .check(CLASSES);
    }

    @Test
    void obsoleteCompatibilityAndFacadeTypesDoNotReturn() {
        List<String> forbiddenNames = List.of(
                "com.aiknowledgeworkspace.workspacecore.asset.AssetService",
                "com.aiknowledgeworkspace.workspacecore.asset.application.compatibility.internal.DirectProcessingCompatibilityAdapter",
                "com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetPersistenceService",
                "com.aiknowledgeworkspace.workspacecore.processing.ProcessingTriggerMode",
                "com.aiknowledgeworkspace.workspacecore.processing.ProcessingProperties"
        );

        assertThat(CLASSES.stream().map(javaClass -> javaClass.getFullName()))
                .doesNotContainAnyElementsOf(forbiddenNames);
    }

    private static boolean isProductType(String packageName) {
        return packageName.startsWith(ROOT);
    }

    private static String moduleName(String packageName) {
        String relativePackage = packageName.substring(ROOT.length());
        int separator = relativePackage.indexOf('.');
        return separator < 0 ? relativePackage : relativePackage.substring(0, separator);
    }
}
