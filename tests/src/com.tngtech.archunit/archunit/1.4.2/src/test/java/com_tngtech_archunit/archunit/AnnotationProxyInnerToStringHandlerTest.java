/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

public class AnnotationProxyInnerToStringHandlerTest {
    @Test
    void formatsAllPropertiesOfAnImportedAnnotationProxy() {
        JavaClass importedClass = new ClassFileImporter().importClass(AnnotatedFixture.class);
        ImportedAnnotation annotation = importedClass.getAnnotationOfType(ImportedAnnotation.class);

        String description = annotation.toString();

        assertThat(description)
                .contains(ImportedAnnotation.class.getName())
                .contains("name=\"fixture module\"")
                .contains(String.class.getName())
                .contains("first")
                .contains("second");
    }
}
