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
import org.springframework.core.annotation.MergedAnnotations;

public class AbstractMergedAnnotationTest {

    @Test
    void readsEnumArrayAttribute() {
        MergedAnnotation<ModeSelection> annotation = MergedAnnotations.from(ModeSelectionTarget.class)
                .get(ModeSelection.class);

        Mode[] modes = annotation.getEnumArray("modes", Mode.class);

        assertThat(modes).containsExactly(Mode.FAST, Mode.SAFE);
    }

    @ModeSelection(modes = {Mode.FAST, Mode.SAFE})
    private static final class ModeSelectionTarget {
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface ModeSelection {

        Mode[] modes();
    }

    public enum Mode {

        FAST,
        SAFE
    }
}
