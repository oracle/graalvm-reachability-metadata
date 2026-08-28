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
import org.springframework.asm.ClassReader;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.classreading.AnnotationMetadataReadingVisitor;

/** Verifies legacy recursive reading of populated and empty annotation arrays. */
@SuppressWarnings("deprecation")
public class RecursiveAnnotationArrayVisitorTest {
    @Test
    void readsEnumArrayAndCreatesTypedEmptyNestedArray() throws Exception {
        AnnotationMetadataReadingVisitor visitor =
                new AnnotationMetadataReadingVisitor(getClass().getClassLoader());
        new ClassReader(AnnotatedFixture.class.getName()).accept(visitor, ClassReader.SKIP_CODE);

        AnnotationAttributes attributes =
                visitor.getAnnotationAttributes(ArrayAnnotation.class.getName(), false);

        assertThat(attributes).isNotNull();
        assertThat((Mode[]) attributes.get("modes")).containsExactly(Mode.FAST, Mode.SAFE);
        assertThat(attributes.getAnnotationArray("nested")).isEmpty();
    }

    public enum Mode {
        FAST,
        SAFE
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Nested {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface ArrayAnnotation {
        Mode[] modes();

        Nested[] nested();
    }

    @ArrayAnnotation(modes = {Mode.FAST, Mode.SAFE}, nested = {})
    private static final class AnnotatedFixture {}
}
