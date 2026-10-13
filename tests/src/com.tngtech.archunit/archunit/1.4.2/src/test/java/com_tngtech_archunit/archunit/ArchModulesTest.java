/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com_tngtech_archunit.archunit.moduleone.ModuleOneRoot;
import com_tngtech_archunit.archunit.moduletwo.ModuleTwoRoot;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.library.modules.ArchModules;
import org.junit.jupiter.api.Test;

public class ArchModulesTest {
    @Test
    void definesNamedModulesFromRuntimeAnnotations() {
        JavaClasses importedClasses =
                new ClassFileImporter().importClasses(ModuleOneRoot.class, ModuleTwoRoot.class);

        ArchModules<?> modules =
                ArchModules.defineByAnnotation(ImportedAnnotation.class).modularize(importedClasses);

        assertThat(modules.getNames()).containsExactlyInAnyOrder("one", "two");
        assertThat(modules.getByName("one"))
                .extracting(javaClass -> javaClass.getName())
                .containsExactly(ModuleOneRoot.class.getName());
        assertThat(modules.getByName("two"))
                .extracting(javaClass -> javaClass.getName())
                .containsExactly(ModuleTwoRoot.class.getName());
    }
}
