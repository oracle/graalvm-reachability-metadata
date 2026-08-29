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

/** Verifies reflective validation of annotation attributes. */
public class AttributeMethodsTest {
    @Test
    @SuppressWarnings("annotationAccess")
    void validatesClassValuedAnnotationAttribute() {
        Component annotation = Annotated.class.getAnnotation(Component.class);

        assertThatCode(() -> AnnotationUtils.validateAnnotation(annotation)).doesNotThrowAnyException();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Component {
        Class<?> type();
    }

    @Component(type = String.class)
    private static final class Annotated {}
}
