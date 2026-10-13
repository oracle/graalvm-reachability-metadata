/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static com.tngtech.archunit.library.modules.syntax.ModuleRuleDefinition.modules;
import static org.assertj.core.api.Assertions.assertThat;

import com_tngtech_archunit.archunit.moduleone.ModuleOneRoot;
import com_tngtech_archunit.archunit.moduletwo.ModuleTwoRoot;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.library.modules.syntax.ModuleDependencyScope;
import org.junit.jupiter.api.Test;

public class ModulesShouldInternalInnerModulesByAnnotationShouldInternalTest {
    @Test
    void readsAllowedModuleNamesFromAnnotationProperty() {
        JavaClasses importedClasses =
                TestClassImporter.importClasses(ModuleOneRoot.class, ModuleTwoRoot.class);

        EvaluationResult result = modules()
                .definedByAnnotation(ImportedAnnotation.class)
                .should()
                .respectTheirAllowedDependenciesDeclaredIn(
                        "allowedDependencies",
                        ModuleDependencyScope.consideringOnlyDependenciesBetweenModules())
                .evaluate(importedClasses);

        assertThat(result.hasViolation()).isFalse();
    }
}
