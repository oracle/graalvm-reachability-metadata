/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;

/** Verifies validation of annotation attributes that can defer missing-type errors. */
public class AttributeMethodsTest {
    @Test
    @SuppressWarnings("checkstyle:annotationAccess")
    void validatesClassValuedAnnotationAttribute() {
        ClassReference annotation = ClassReferenceFixture.class.getAnnotation(ClassReference.class);

        assertThatCode(() -> AnnotationUtils.validateAnnotation(annotation)).doesNotThrowAnyException();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface ClassReference {
        Class<?> value();
    }

    @ClassReference(String.class)
    private static final class ClassReferenceFixture {}
}
