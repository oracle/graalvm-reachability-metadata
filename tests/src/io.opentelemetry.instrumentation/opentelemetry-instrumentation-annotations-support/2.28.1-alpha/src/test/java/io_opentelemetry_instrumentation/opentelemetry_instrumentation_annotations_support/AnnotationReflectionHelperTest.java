/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_instrumentation_annotations_support;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.annotation.support.AnnotationReflectionHelper;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class AnnotationReflectionHelperTest {
    @Test
    void findsAnnotationClassByName() {
        ClassLoader classLoader = AnnotationReflectionHelperTest.class.getClassLoader();

        Class<? extends Annotation> annotationClass = AnnotationReflectionHelper.forNameOrNull(
                classLoader, SpanLabel.class.getName());

        assertThat(annotationClass).isEqualTo(SpanLabel.class);
    }

    @Test
    void returnsNullForUnavailableAnnotationClass() {
        ClassLoader classLoader = AnnotationReflectionHelperTest.class.getClassLoader();

        Class<? extends Annotation> annotationClass = AnnotationReflectionHelper.forNameOrNull(
                classLoader, "example.DoesNotExist");

        assertThat(annotationClass).isNull();
    }

    @Test
    void bindsAnnotationElementMethod() throws Throwable {
        Function<SpanLabel, String> readValue =
                AnnotationReflectionHelper.bindAnnotationElementMethod(
                        MethodHandles.lookup(), SpanLabel.class, "value", String.class);
        Function<SpanLabel, Integer> readPriority =
                AnnotationReflectionHelper.bindAnnotationElementMethod(
                        MethodHandles.lookup(), SpanLabel.class, "priority", int.class);

        SpanLabel annotation = new SpanLabelInstance("checkout", 7);

        assertThat(readValue.apply(annotation)).isEqualTo("checkout");
        assertThat(readPriority.apply(annotation)).isEqualTo(7);
    }

    public @interface SpanLabel {
        String value();

        int priority() default 1;
    }

    private static final class SpanLabelInstance implements SpanLabel {
        private final String value;
        private final int priority;

        private SpanLabelInstance(String value, int priority) {
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
            return SpanLabel.class;
        }
    }
}
