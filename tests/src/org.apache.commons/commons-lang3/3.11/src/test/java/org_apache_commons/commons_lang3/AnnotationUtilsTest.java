/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.commons.lang3.AnnotationUtils;
import org.junit.jupiter.api.Test;

public class AnnotationUtilsTest {

    @Test
    void comparesRuntimeAnnotationsUsingTheirDeclaredMembers() {
        final SampleAnnotation first = MatchingAnnotatedType.class.getAnnotationsByType(SampleAnnotation.class)[0];
        final SampleAnnotation same = EquivalentAnnotatedType.class.getAnnotationsByType(SampleAnnotation.class)[0];
        final SampleAnnotation different = DifferentAnnotatedType.class.getAnnotationsByType(SampleAnnotation.class)[0];

        assertThat(AnnotationUtils.equals(first, first)).isTrue();
        assertThat(AnnotationUtils.equals(first, same)).isTrue();
        assertThat(AnnotationUtils.equals(first, different)).isFalse();
        assertThat(AnnotationUtils.equals(first, null)).isFalse();
    }

    @Test
    void calculatesTheSameHashCodeAsTheRuntimeAnnotationContract() {
        final SampleAnnotation first = MatchingAnnotatedType.class.getAnnotationsByType(SampleAnnotation.class)[0];
        final SampleAnnotation same = EquivalentAnnotatedType.class.getAnnotationsByType(SampleAnnotation.class)[0];
        final SampleAnnotation different = DifferentAnnotatedType.class.getAnnotationsByType(SampleAnnotation.class)[0];

        assertThat(AnnotationUtils.hashCode(first)).isEqualTo(first.hashCode());
        assertThat(AnnotationUtils.hashCode(same)).isEqualTo(same.hashCode());
        assertThat(AnnotationUtils.hashCode(different)).isEqualTo(different.hashCode());
        assertThat(AnnotationUtils.hashCode(first)).isEqualTo(AnnotationUtils.hashCode(same));
    }

    @Test
    void rendersRuntimeAnnotationsIncludingNestedMembers() {
        final SampleAnnotation annotation = MatchingAnnotatedType.class.getAnnotationsByType(SampleAnnotation.class)[0];

        final String description = AnnotationUtils.toString(annotation);

        assertThat(description).startsWith("@" + SampleAnnotation.class.getName() + "(");
        assertThat(description).contains("text=alpha");
        assertThat(description).contains("numbers=[1,2,3]");
        assertThat(description).contains("type=class java.lang.String");
        assertThat(description).contains("nested=@" + NestedAnnotation.class.getName() + "(name=inner)");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface NestedAnnotation {
        String name();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface SampleAnnotation {
        String text();

        int[] numbers();

        Class<?> type();

        NestedAnnotation nested();
    }

    @SampleAnnotation(
            text = "alpha",
            numbers = { 1, 2, 3 },
            type = String.class,
            nested = @NestedAnnotation(name = "inner")
    )
    private static final class MatchingAnnotatedType {
    }

    @SampleAnnotation(
            text = "alpha",
            numbers = { 1, 2, 3 },
            type = String.class,
            nested = @NestedAnnotation(name = "inner")
    )
    private static final class EquivalentAnnotatedType {
    }

    @SampleAnnotation(
            text = "beta",
            numbers = { 9, 8, 7 },
            type = Integer.class,
            nested = @NestedAnnotation(name = "different")
    )
    private static final class DifferentAnnotatedType {
    }
}
