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

public class AnnotationAttributesTest {

    @Test
    void resolvesAnnotationTypeByNameWithClassLoader() {
        ClassLoader classLoader = AnnotationAttributesTest.class.getClassLoader();
        String annotationTypeName = SampleAnnotation.class.getName();

        AnnotationAttributes attributes = new AnnotationAttributes(annotationTypeName, classLoader);

        assertThat(attributes.annotationType()).isEqualTo(SampleAnnotation.class);
    }

    @Test
    void returnsSingleAnnotationAsAnnotationArray() {
        SampleAnnotation annotation = AnnotatedSample.class.getAnnotation(SampleAnnotation.class);
        AnnotationAttributes attributes = new AnnotationAttributes(SampleContainer.class);
        attributes.put("value", annotation);

        SampleAnnotation[] annotations = attributes.getAnnotationArray("value", SampleAnnotation.class);

        assertThat(annotations).hasSize(1);
        assertThat(annotations[0].value()).isEqualTo("sample");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface SampleContainer {

        SampleAnnotation[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface SampleAnnotation {

        String value();
    }

    @SampleAnnotation("sample")
    static class AnnotatedSample {
    }
}
