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
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

/** Verifies typed annotation-array materialization during ASM metadata reading. */
public class MergedAnnotationReadingVisitorInnerArrayVisitorTest {
    @Test
    void readsTypedEnumAndNestedAnnotationArrays() throws Exception {
        MetadataReader reader = new SimpleMetadataReaderFactory(getClass().getClassLoader())
                .getMetadataReader(AnnotatedFixture.class.getName());
        MergedAnnotation<ArrayAnnotation> annotation =
                reader.getAnnotationMetadata().getAnnotations().get(ArrayAnnotation.class);

        assertThat(annotation.getEnumArray("modes", Mode.class)).containsExactly(Mode.FAST, Mode.SAFE);
        assertThat(annotation.getAnnotationArray("nested", Nested.class)).hasSize(2);
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

    @ArrayAnnotation(
            modes = {Mode.FAST, Mode.SAFE},
            nested = {@Nested("one"), @Nested("two")})
    private static final class AnnotatedFixture {}
}
