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

/** Verifies synthesis of a bootstrap-loaded annotation type. */
public class SynthesizedMergedAnnotationInvocationHandlerTest {
    @Test
    void synthesizesAnnotationWhoseLoaderCannotSeeSpringInterfaces() {
        MergedAnnotation<Deprecated> merged = MergedAnnotation.of(
                Deprecated.class, Collections.singletonMap("since", "spring"));

        Deprecated synthesized = merged.synthesize();

        assertThat(synthesized.since()).isEqualTo("spring");
        assertThat(synthesized.forRemoval()).isFalse();
    }
}
