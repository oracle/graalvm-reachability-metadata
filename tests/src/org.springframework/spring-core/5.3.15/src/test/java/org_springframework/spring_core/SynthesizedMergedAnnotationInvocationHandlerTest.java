/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.MergedAnnotation;

/** Verifies synthesis of a bootstrap-loaded annotation through Spring's merged annotation API. */
public class SynthesizedMergedAnnotationInvocationHandlerTest {
    @Test
    void synthesizesBootstrapLoadedAnnotation() {
        Deprecated annotation = MergedAnnotation.of(Deprecated.class).synthesize();

        assertThat(annotation.annotationType()).isEqualTo(Deprecated.class);
        assertThat(annotation.since()).isEmpty();
        assertThat(annotation.forRemoval()).isFalse();
    }
}
