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
import org.springframework.core.annotation.AnnotationAttributes;

/** Verifies annotation type loading and scalar-to-array adaptation. */
public class AnnotationAttributesTest {
    @Test
    void loadsAnnotationTypeAndAdaptsSingleAnnotationToArray() {
        AnnotationAttributes attributes = new AnnotationAttributes(
                Marker.class.getName(), Marker.class.getClassLoader());
        Marker marker = Annotated.class.getAnnotation(Marker.class);
        attributes.put("markers", marker);

        assertThat(attributes.annotationType()).isEqualTo(Marker.class);
        assertThat(attributes.getAnnotationArray("markers", Marker.class)).containsExactly(marker);
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Marker {
        String value();
    }

    @Marker("spring")
    private static final class Annotated {}
}
