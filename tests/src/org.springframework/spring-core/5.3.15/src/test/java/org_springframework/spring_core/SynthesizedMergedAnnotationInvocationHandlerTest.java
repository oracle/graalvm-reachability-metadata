/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.MergedAnnotation;

/** Verifies synthesis when a bootstrap-loaded annotation cannot see Spring interfaces. */
public class SynthesizedMergedAnnotationInvocationHandlerTest {
    @Test
    void synthesizesBootstrapLoadedAnnotation() {
        MergedAnnotation<Deprecated> merged = MergedAnnotation.of(
                Deprecated.class, Collections.singletonMap("since", "spring-core"));

        Deprecated synthesized = merged.synthesize();

        assertThat(synthesized.annotationType()).isEqualTo(Deprecated.class);
        assertThat(synthesized.since()).isEqualTo("spring-core");
        assertThat(synthesized.forRemoval()).isFalse();
    }
}
