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
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.MergedAnnotation;

/** Verifies typed enum-array access from a merged annotation. */
public class AbstractMergedAnnotationTest {
    @Test
    void returnsTypedEnumArray() {
        MergedAnnotation<ModeSelection> annotation = MergedAnnotation.of(
                ModeSelection.class, Collections.singletonMap("value", new Mode[] {Mode.FAST, Mode.SAFE}));

        assertThat(annotation.getEnumArray("value", Mode.class)).containsExactly(Mode.FAST, Mode.SAFE);
    }

    public enum Mode {
        FAST,
        SAFE
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface ModeSelection {
        Mode[] value();
    }
}
