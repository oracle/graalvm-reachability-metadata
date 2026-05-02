/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;

public class AttributeMethodsTest {

    @Test
    void validatesAnnotationAttributesThatMayThrowTypeNotPresentException() {
        ClassBackedAnnotation annotation = ClassBackedAnnotatedType.class.getAnnotation(ClassBackedAnnotation.class);
        assertThat(annotation).isNotNull();

        AnnotationUtils.validateAnnotation(annotation);

        assertThat(annotation.primaryType()).isEqualTo(String.class);
        assertThat(annotation.supportedTypes()).containsExactly(Integer.class, Long.class);
        assertThat(annotation.category()).isEqualTo(AttributeCategory.STANDARD);
    }

    @ClassBackedAnnotation(
            primaryType = String.class,
            supportedTypes = {Integer.class, Long.class},
            category = AttributeCategory.STANDARD)
    static class ClassBackedAnnotatedType {
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface ClassBackedAnnotation {

        Class<?> primaryType();

        Class<?>[] supportedTypes();

        AttributeCategory category();
    }

    public enum AttributeCategory {

        STANDARD
    }
}
