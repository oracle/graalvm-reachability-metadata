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

import org_springframework.spring_core.TypeMappedAnnotationTest.NestedAnnotation;

/** Verifies synthesis of an application-loaded annotation type. */
public class SynthesizedMergedAnnotationInvocationHandlerTest {
    @Test
    void synthesizesApplicationLoadedAnnotation() {
        MergedAnnotation<NestedAnnotation> mergedAnnotation =
                MergedAnnotation.of(NestedAnnotation.class, Collections.singletonMap("value", "native"));

        NestedAnnotation synthesized = mergedAnnotation.synthesize();

        assertThat(synthesized.annotationType()).isEqualTo(NestedAnnotation.class);
        assertThat(synthesized.value()).isEqualTo("native");
    }
}
