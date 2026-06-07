/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.annotation.AnnotationDescription;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationDescriptionInnerAnnotationInvocationHandlerTest {
    @Test
    void loadsLatentAnnotationDescriptionAsRuntimeAnnotationProxy() throws Exception {
        InvocationHandlerAnnotation annotation = AnnotationDescription.Builder
                .ofType(InvocationHandlerAnnotation.class)
                .define("name", "byte-buddy")
                .define("count", 42)
                .define("target", Integer.class)
                .build()
                .prepare(InvocationHandlerAnnotation.class)
                .load();

        assertThat(annotation.annotationType()).isEqualTo(InvocationHandlerAnnotation.class);
        assertThat(annotation.name()).isEqualTo("byte-buddy");
        assertThat(annotation.count()).isEqualTo(42);
        assertThat(annotation.target()).isEqualTo(Integer.class);
    }

    @Test
    void comparesLoadedAnnotationProxyToRuntimeAnnotationInstance() throws Exception {
        InvocationHandlerAnnotation annotation = AnnotationDescription.Builder
                .ofType(InvocationHandlerAnnotation.class)
                .define("name", "runtime")
                .define("count", 7)
                .define("target", Long.class)
                .build()
                .prepare(InvocationHandlerAnnotation.class)
                .load();
        InvocationHandlerAnnotation runtimeAnnotation = AnnotatedType.class.getDeclaredAnnotationsByType(
                InvocationHandlerAnnotation.class)[0];

        assertThat(annotation).isEqualTo(runtimeAnnotation);
        assertThat(annotation.hashCode()).isEqualTo(runtimeAnnotation.hashCode());
        assertThat(annotation.toString()).contains("name=runtime", "count=7");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface InvocationHandlerAnnotation {
        String name();

        int count() default 1;

        Class<?> target() default String.class;
    }

    @InvocationHandlerAnnotation(name = "runtime", count = 7, target = Long.class)
    private static class AnnotatedType {
    }
}
