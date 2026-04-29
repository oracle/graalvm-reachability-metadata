/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.description.annotation.AnnotationDescription;

public class AnnotationDescriptionInnerAnnotationInvocationHandlerTest {
    @Test
    void loadsAnnotationProxyAndComparesMemberValuesWithAnnotationImplementation() {
        GeneratedAnnotation annotation = AnnotationDescription.Builder.ofType(GeneratedAnnotation.class)
            .define("value", "modelmapper")
            .build()
            .prepare(GeneratedAnnotation.class)
            .load();

        GeneratedAnnotation expectedAnnotation = new GeneratedAnnotationImplementation("modelmapper", 42);

        assertThat(annotation.annotationType()).isEqualTo(GeneratedAnnotation.class);
        assertThat(annotation.value()).isEqualTo("modelmapper");
        assertThat(annotation.priority()).isEqualTo(42);
        assertThat(annotation).isEqualTo(expectedAnnotation);
    }

    public @interface GeneratedAnnotation {
        String value();

        int priority() default 42;
    }

    public static final class GeneratedAnnotationImplementation implements GeneratedAnnotation {
        private final String value;
        private final int priority;

        private GeneratedAnnotationImplementation(String value, int priority) {
            this.value = value;
            this.priority = priority;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return GeneratedAnnotation.class;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof GeneratedAnnotation)) {
                return false;
            }
            GeneratedAnnotation annotation = (GeneratedAnnotation) other;
            return value.equals(annotation.value()) && priority == annotation.priority();
        }

        @Override
        public int hashCode() {
            return ((127 * "value".hashCode()) ^ value.hashCode())
                + ((127 * "priority".hashCode()) ^ Integer.hashCode(priority));
        }
    }
}
