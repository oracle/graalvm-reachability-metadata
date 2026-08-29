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

/** Verifies typed enum-array access from merged annotations. */
public class AbstractMergedAnnotationTest {
    @Test
    @SuppressWarnings("annotationAccess")
    void returnsTypedEnumArray() {
        Modes annotation = Annotated.class.getAnnotation(Modes.class);
        MergedAnnotation<Modes> merged = MergedAnnotation.from(annotation);

        assertThat(merged.getEnumArray("value", Mode.class)).containsExactly(Mode.FAST, Mode.SAFE);
    }

    public enum Mode {
        FAST,
        SAFE
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Modes {
        Mode[] value();
    }

    @Modes({Mode.FAST, Mode.SAFE})
    private static final class Annotated {}
}
