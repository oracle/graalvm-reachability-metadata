/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.Test;

public class JavaClassProcessorInnerAnnotationArrayProcessorInnerArrayValueBuilderTest {
    @Test
    void importsAnnotationArraysWithTheirCompleteElementTypes() {
        JavaClass importedClass = TestClassImporter.importClass(AnnotatedFixture.class);
        JavaAnnotation<?> annotation =
                importedClass.getAnnotationOfType(ImportedAnnotation.class.getName());

        assertThat(annotation.get("types").orElseThrow()).isInstanceOf(Object[].class);
        assertThat((Object[]) annotation.get("types").orElseThrow()).hasSize(2);
        assertThat(annotation.get("nested").orElseThrow()).isInstanceOf(Object[].class);
        assertThat((Object[]) annotation.get("nested").orElseThrow()).hasSize(2);
    }
}
