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
    void loadsAnnotationTypeAndWrapsScalarAttributeInArray() {
        AnnotationAttributes attributes = new AnnotationAttributes(
                Labels.class.getName(), Labels.class.getClassLoader());
        attributes.put("value", "spring");

        assertThat(attributes.annotationType()).isEqualTo(Labels.class);
        assertThat(attributes.getStringArray("value")).containsExactly("spring");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Labels {
        String[] value();
    }
}
