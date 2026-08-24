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
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotation.Adapt;

/** Verifies Spring's mapped annotation value adaptations. */
public class TypeMappedAnnotationTest {
    @Test
    void adaptsScalarNestedAnnotationAndEmptyObjectArrays() {
        Map<String, Object> nestedAttributes = nestedAttributes("first");
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("nested", nestedAttributes);
        attributes.put("types", new Object[0]);
        MergedAnnotation<ContainerAnnotation> annotation =
                MergedAnnotation.of(ContainerAnnotation.class, attributes);

        NestedAnnotation[] nested = annotation
                .getValue("nested", NestedAnnotation[].class)
                .orElseThrow(AssertionError::new);

        assertThat(nested).extracting(NestedAnnotation::value).containsExactly("first");
        assertThat(annotation.getClassArray("types")).isEmpty();
    }

    @Test
    void exportsNestedAnnotationArrayAsAnnotationsAndMaps() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("nested", new Object[] {nestedAttributes("first"), nestedAttributes("second")});
        MergedAnnotation<ContainerAnnotation> annotation =
                MergedAnnotation.of(ContainerAnnotation.class, attributes);

        Map<String, Object> annotationValues = annotation.asMap();
        Map<String, Object> mapValues = annotation.asMap(Adapt.ANNOTATION_TO_MAP);
        Map<?, ?>[] nestedMaps = (Map<?, ?>[]) mapValues.get("nested");

        assertThat((NestedAnnotation[]) annotationValues.get("nested"))
                .extracting(NestedAnnotation::value)
                .containsExactly("first", "second");
        assertThat(nestedMaps).hasSize(2);
        assertThat(nestedMaps[0].get("value")).isEqualTo("first");
        assertThat(nestedMaps[1].get("value")).isEqualTo("second");
    }

    private static Map<String, Object> nestedAttributes(String value) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("value", value);
        return attributes;
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface ContainerAnnotation {
        NestedAnnotation[] nested();

        Class<?>[] types() default {};
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface NestedAnnotation {
        String value();
    }
}
