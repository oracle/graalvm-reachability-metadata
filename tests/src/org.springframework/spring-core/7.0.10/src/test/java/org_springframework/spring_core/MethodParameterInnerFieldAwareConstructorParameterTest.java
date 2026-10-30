/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;

/** Verifies field annotations augment constructor parameter annotations. */
public class MethodParameterInnerFieldAwareConstructorParameterTest {
    @Test
    void exposesAnnotationOnCorrespondingField() throws Exception {
        MethodParameter parameter = MethodParameter.forFieldAwareConstructor(
                Contact.class.getConstructor(String.class), 0, "email");

        assertThat(parameter.getParameterAnnotation(FieldLabel.class).value()).isEqualTo("email-address");
        assertThat(parameter.getParameterAnnotation(ParameterLabel.class).value()).isEqualTo("input");
        assertThat(parameter.getParameterAnnotations()).hasSize(2);
        assertThat(parameter.getParameterName()).isEqualTo("email");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface FieldLabel {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface ParameterLabel {
        String value();
    }

    public static class Contact {
        @FieldLabel("email-address")
        private final String email;

        public Contact(@ParameterLabel("input") String email) {
            this.email = email;
        }
    }
}
