/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AliasFor;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;

public class AnnotationUtilsTest {

    @Test
    void getsNamedAttributeValueFromAnnotation() {
        NamedAnnotation annotation = NamedAnnotatedType.class.getAnnotation(NamedAnnotation.class);

        Object value = AnnotationUtils.getValue(annotation, "value");

        assertThat(value).isEqualTo("direct-value");
    }

    @Test
    void postProcessesMirroredNestedAnnotationArrays() {
        AliasGroupAnnotation annotation = AliasGroupAnnotatedType.class.getAnnotation(AliasGroupAnnotation.class);
        AnnotationAttributes attributes = new AnnotationAttributes(AliasGroupAnnotation.class);
        attributes.put("value", annotation.value());
        attributes.put("children", annotation.children());

        AnnotationUtils.postProcessAnnotationAttributes(AliasGroupAnnotatedType.class, attributes, false);

        NestedAnnotation[] children = (NestedAnnotation[]) attributes.get("children");
        assertThat(children).extracting(NestedAnnotation::name).containsExactly("alpha", "bravo");
    }

    @SuppressWarnings("deprecation")
    @Test
    void synthesizesAnnotationArraysFromAnnotatedElement() {
        Annotation[] annotations = AnnotationUtils.getAnnotations(AliasGroupAnnotatedType.class);

        assertThat(annotations).isNotNull();
        assertThat(annotations).hasSize(1);
        assertThat(annotations[0]).isInstanceOf(AliasGroupAnnotation.class);
        AliasGroupAnnotation annotation = (AliasGroupAnnotation) annotations[0];
        assertThat(annotation.children()).extracting(NestedAnnotation::name).containsExactly("alpha", "bravo");
    }

    @NamedAnnotation("direct-value")
    static class NamedAnnotatedType {
    }

    @AliasGroupAnnotation({@NestedAnnotation(name = "alpha"), @NestedAnnotation(name = "bravo")})
    static class AliasGroupAnnotatedType {
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface NamedAnnotation {

        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface AliasGroupAnnotation {

        @AliasFor("children")
        NestedAnnotation[] value() default {};

        @AliasFor("value")
        NestedAnnotation[] children() default {};
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface NestedAnnotation {

        String name();
    }
}
