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

/** Verifies synthesis of a bootstrap-loaded annotation type. */
public class SynthesizedMergedAnnotationInvocationHandlerTest {
    @Test
    void synthesizesBootstrapLoadedAnnotation() {
        MergedAnnotation<Retention> mergedAnnotation = MergedAnnotation.of(
                Retention.class, Collections.singletonMap("value", RetentionPolicy.RUNTIME));

        Retention synthesized = mergedAnnotation.synthesize();

        assertThat(synthesized.annotationType()).isEqualTo(Retention.class);
        assertThat(synthesized.value()).isEqualTo(RetentionPolicy.RUNTIME);
    }
}
