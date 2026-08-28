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

/** Verifies legacy annotation metadata resolution of enum constants. */
@SuppressWarnings("deprecation")
public class AbstractRecursiveAnnotationVisitorTest {
    @Test
    void resolvesEnumConstantWhileReadingClassMetadata() throws Exception {
        AnnotationMetadataReadingVisitor visitor =
                new AnnotationMetadataReadingVisitor(getClass().getClassLoader());
        new ClassReader(AnnotatedFixture.class.getName()).accept(visitor, ClassReader.SKIP_CODE);

        AnnotationAttributes attributes =
                visitor.getAnnotationAttributes(ModeAnnotation.class.getName(), false);

        assertThat(attributes).isNotNull();
        Mode mode = attributes.getEnum("value");
        assertThat(mode).isEqualTo(Mode.SAFE);
    }

    public enum Mode {
        FAST,
        SAFE
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface ModeAnnotation {
        Mode value();
    }

    @ModeAnnotation(Mode.SAFE)
    private static final class AnnotatedFixture {}
}
