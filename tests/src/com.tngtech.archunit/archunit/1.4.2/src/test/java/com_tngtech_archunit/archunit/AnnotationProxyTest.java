/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.Test;

public class AnnotationProxyTest {
    @Test
    void convertsImportedAnnotationValuesToRuntimeTypes() {
        JavaClass importedClass = TestClassImporter.importClass(AnnotatedFixture.class);

        ImportedAnnotation annotation = importedClass.getAnnotationOfType(ImportedAnnotation.class);

        assertThat(annotation.name()).isEqualTo("fixture module");
        assertThat(annotation.types()).containsExactly(String.class, Integer.class);
        assertThat(annotation.nested())
                .extracting(NestedAnnotation::value)
                .containsExactly("first", "second");
        assertThat(annotation.annotationType()).isEqualTo(ImportedAnnotation.class);
    }
}
