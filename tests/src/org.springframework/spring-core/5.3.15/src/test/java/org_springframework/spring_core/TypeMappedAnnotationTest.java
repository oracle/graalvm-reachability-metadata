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

public class TypeMappedAnnotationTest {

    @Test
    void adaptsSingleNestedAnnotationMapToAnnotationArray() {
        Map<String, Object> nestedAttributes = new LinkedHashMap<>();
        nestedAttributes.put("name", "single");
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("nestedAnnotations", nestedAttributes);

        NestedAnnotation[] nestedAnnotations = MergedAnnotation.of(CompositeAnnotation.class, attributes)
                .getValue("nestedAnnotations", NestedAnnotation[].class)
                .orElseThrow(AssertionError::new);

        assertThat(nestedAnnotations).hasSize(1);
        assertThat(nestedAnnotations[0].name()).isEqualTo("single");
    }

    @Test
    void adaptsNestedAnnotationArrayToMapArrayWhenRequested() {
        Map<String, Object> attributes = compositeAttributes("alpha", "bravo");

        Object nestedAnnotationMaps = MergedAnnotation.of(CompositeAnnotation.class, attributes)
                .asMap(MergedAnnotation.Adapt.ANNOTATION_TO_MAP)
                .get("nestedAnnotations");

        assertThat(nestedAnnotationMaps).isInstanceOf(Map[].class);
        Map<?, ?>[] maps = (Map<?, ?>[]) nestedAnnotationMaps;
        assertThat(maps).hasSize(2);
        assertThat(maps[0].get("name")).isEqualTo("alpha");
        assertThat(maps[1].get("name")).isEqualTo("bravo");
    }

    @Test
    void adaptsNestedAnnotationArrayToSynthesizedAnnotationArrayForMaps() {
        Map<String, Object> attributes = compositeAttributes("charlie", "delta");

        Object nestedAnnotations = MergedAnnotation.of(CompositeAnnotation.class, attributes)
                .asMap()
                .get("nestedAnnotations");

        assertThat(nestedAnnotations).isInstanceOf(NestedAnnotation[].class);
        NestedAnnotation[] annotations = (NestedAnnotation[]) nestedAnnotations;
        assertThat(annotations).hasSize(2);
        assertThat(annotations[0].name()).isEqualTo("charlie");
        assertThat(annotations[1].name()).isEqualTo("delta");
    }

    @Test
    void adaptsEmptyObjectArrayToTypedClassArray() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("types", new Object[0]);

        Class<?>[] classes = MergedAnnotation.of(ClassArrayAnnotation.class, attributes)
                .getClassArray("types");

        assertThat(classes).isEmpty();
    }

    private Map<String, Object> compositeAttributes(String firstName, String secondName) {
        Map<String, Object> firstNestedAnnotation = new LinkedHashMap<>();
        firstNestedAnnotation.put("name", firstName);
        Map<String, Object> secondNestedAnnotation = new LinkedHashMap<>();
        secondNestedAnnotation.put("name", secondName);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("nestedAnnotations", new Map[] {firstNestedAnnotation, secondNestedAnnotation});
        return attributes;
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface CompositeAnnotation {

        NestedAnnotation[] nestedAnnotations();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface NestedAnnotation {

        String name();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface ClassArrayAnnotation {

        Class<?>[] types();
    }
}
