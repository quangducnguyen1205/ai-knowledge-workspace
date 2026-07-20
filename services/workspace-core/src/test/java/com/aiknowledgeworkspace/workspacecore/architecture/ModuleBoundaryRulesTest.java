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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Service;

class ModuleBoundaryRulesTest {

    private static final String ROOT = "com.aiknowledgeworkspace.workspacecore.";
    private static final Set<String> MODULE_NAMES = Set.of(
            "asset",
            "assistant",
            "common",
            "identity",
            "integration",
            "outbox",
            "processing",
            "search",
            "storage",
            "workspace"
    );
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.aiknowledgeworkspace.workspacecore");

    @Test
    void applicationLayersDoNotDependOnAdaptersOrInfrastructure() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..adapter..", "..infrastructure..")
                .check(CLASSES);
    }

    @Test
    void domainLayersDoNotDependOnApplicationOrAdapters() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..application..",
                        "..adapter..",
                        "..infrastructure.."
                )
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
                .filter(javaClass -> javaClass.getPackageName().contains(".adapter.in.")
                        || javaClass.getSimpleName().endsWith("Controller")
                        || javaClass.getSimpleName().endsWith("Listener")
                        || javaClass.getSimpleName().endsWith("Scheduler"))
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> dependency.getTargetClass().isAssignableTo(Repository.class))
                .toList();

        assertThat(repositoryDependencies)
                .as("inbound adapters must enter through application contracts")
                .isEmpty();
    }

    @Test
    void messageListenersDoNotDependOnConcreteApplicationServices() {
        noClasses()
                .that().haveSimpleNameEndingWith("Listener")
                .should().dependOnClassesThat().areAnnotatedWith(Service.class)
                .check(CLASSES);
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
                .filter(dependency -> dependency.getTargetClass().isAssignableTo(Repository.class)
                        || dependency.getTargetClass().isAnnotatedWith(Entity.class))
                .toList();

        assertThat(forbiddenDependencies)
                .as("repositories and JPA entities are private implementation details of their owning module")
                .isEmpty();
    }

    @Test
    void commonAndOutboxRemainProductFeatureNeutral() {
        noClasses()
                .that().resideInAnyPackage(ROOT + "common..", ROOT + "outbox..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + "asset..",
                        ROOT + "assistant..",
                        ROOT + "identity..",
                        ROOT + "integration..",
                        ROOT + "processing..",
                        ROOT + "search..",
                        ROOT + "storage..",
                        ROOT + "workspace.."
                )
                .check(CLASSES);
    }

    @Test
    void productModulesDoNotDependOnFastApiProviderTypes() {
        noClasses()
                .that().resideOutsideOfPackage("..integration..")
                .should().dependOnClassesThat().resideInAPackage("..integration.fastapi.adapter.out.provider..")
                .check(CLASSES);
    }

    @Test
    void nonSearchModulesDoNotDependOnSearchEngineAdapters() {
        noClasses()
                .that().resideOutsideOfPackage("..search..")
                .should().dependOnClassesThat().resideInAPackage("..search.adapter.out.search..")
                .check(CLASSES);
    }

    @Test
    void indexingTransactionDoesNotPerformExternalIndexCalls() {
        noClasses()
                .that().haveSimpleName("IndexingAttemptTransactionService")
                .should().dependOnClassesThat().haveSimpleName("TranscriptIndexWriter")
                .orShould().dependOnClassesThat().haveSimpleName("ElasticsearchTranscriptAdapter")
                .check(CLASSES);
    }

    @Test
    void moduleBasePackagesDoNotExposeAccidentalApis() {
        List<String> directModuleTypes = CLASSES.stream()
                .filter(javaClass -> MODULE_NAMES.contains(moduleName(javaClass.getPackageName())))
                .filter(javaClass -> javaClass.getPackageName().equals(ROOT + moduleName(javaClass.getPackageName())))
                .map(javaClass -> javaClass.getFullName())
                .sorted()
                .toList();

        assertThat(directModuleTypes)
                .as("module base packages must not become accidental public surfaces")
                .isEmpty();
    }

    @Test
    void obsoleteCompatibilityAndFacadeTypesDoNotReturn() {
        List<String> forbiddenSimpleNames = List.of(
                "AssetService",
                "AssetPersistenceService",
                "DirectProcessingCompatibilityAdapter",
                "ProcessingTriggerMode",
                "ProcessingProperties",
                "WorkspaceQueryApplication",
                "ProcessingJobUpdateCommand"
        );

        assertThat(CLASSES.stream().map(javaClass -> javaClass.getSimpleName()))
                .doesNotContainAnyElementsOf(forbiddenSimpleNames);
    }

    private static boolean isProductType(String packageName) {
        return packageName.startsWith(ROOT);
    }

    private static String moduleName(String packageName) {
        if (!packageName.startsWith(ROOT)) {
            return "";
        }
        String relativePackage = packageName.substring(ROOT.length());
        int separator = relativePackage.indexOf('.');
        return separator < 0 ? relativePackage : relativePackage.substring(0, separator);
    }
}
