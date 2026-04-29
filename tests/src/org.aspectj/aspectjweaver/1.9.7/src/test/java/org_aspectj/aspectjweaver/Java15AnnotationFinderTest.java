/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;

import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.reflect.Java15AnnotationFinder;
import org.aspectj.weaver.reflect.ReflectionWorld;
import org.junit.jupiter.api.Test;

public class Java15AnnotationFinderTest {
    @Test
    void findsRuntimeAnnotationOnObjectClass() {
        Java15AnnotationFinder finder = annotationFinder();
        ResolvedType annotationType = resolvedAnnotationType();

        Object annotation = finder.getAnnotation(annotationType, new AnnotatedFixture());

        assertThat(annotation)
                .isInstanceOf(TrackedAnnotation.class)
                .extracting(value -> ((TrackedAnnotation) value).value())
                .isEqualTo("type-level");
    }

    @Test
    void findsRuntimeAnnotationFromClassToken() {
        Java15AnnotationFinder finder = annotationFinder();
        ResolvedType annotationType = resolvedAnnotationType();

        Object annotation = finder.getAnnotationFromClass(annotationType, AnnotatedFixture.class);

        assertThat(annotation)
                .isInstanceOf(TrackedAnnotation.class)
                .extracting(value -> ((TrackedAnnotation) value).value())
                .isEqualTo("type-level");
    }

    @Test
    @TrackedAnnotation("method-level")
    void findsRuntimeAnnotationFromMember() throws Exception {
        Java15AnnotationFinder finder = annotationFinder();
        ResolvedType annotationType = resolvedAnnotationType();
        Method member = Java15AnnotationFinderTest.class.getDeclaredMethod("findsRuntimeAnnotationFromMember");

        Object annotation = finder.getAnnotationFromMember(annotationType, member);

        assertThat(annotation)
                .isInstanceOf(TrackedAnnotation.class)
                .extracting(value -> ((TrackedAnnotation) value).value())
                .isEqualTo("method-level");
    }

    private static Java15AnnotationFinder annotationFinder() {
        Java15AnnotationFinder finder = new Java15AnnotationFinder();
        finder.setClassLoader(Java15AnnotationFinderTest.class.getClassLoader());
        return finder;
    }

    private static ResolvedType resolvedAnnotationType() {
        ReflectionWorld world = new ReflectionWorld(Java15AnnotationFinderTest.class.getClassLoader());
        return world.resolve(TrackedAnnotation.class);
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface TrackedAnnotation {
        String value();
    }

    @TrackedAnnotation("type-level")
    private static final class AnnotatedFixture {
    }
}
