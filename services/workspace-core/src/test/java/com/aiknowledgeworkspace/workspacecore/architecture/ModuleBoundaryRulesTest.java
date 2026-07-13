package com.aiknowledgeworkspace.workspacecore.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

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

}
