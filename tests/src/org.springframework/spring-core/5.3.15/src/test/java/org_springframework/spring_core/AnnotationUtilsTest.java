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

/** Verifies Spring's public annotation introspection and synthesis APIs. §FS-repository-functional-spec.5.2 */
public class AnnotationUtilsTest {
    @Test
    void retrievesAnnotationAttributeValue() {
        ValueAnnotation annotation =
                AnnotationUtils.getAnnotation(AnnotatedFixture.class, ValueAnnotation.class);

        assertThat(annotation).isNotNull();
        assertThat(AnnotationUtils.getValue(annotation)).isEqualTo("spring");
        assertThat(AnnotationUtils.getValue(annotation, "value")).isEqualTo("spring");
    }

    @Test
    void adaptsNestedAnnotationArrayForAliasedAttribute() {
        NestedHolder holder =
                AnnotationUtils.getAnnotation(NestedHolderFixture.class, NestedHolder.class);
        assertThat(holder).isNotNull();
        NestedAnnotation[] original = holder.value();
        AnnotationAttributes attributes = new AnnotationAttributes(AliasedNestedAnnotations.class);
        attributes.put("value", original);

        AnnotationUtils.postProcessAnnotationAttributes(NestedHolderFixture.class, attributes, false);

        NestedAnnotation[] mirrored =
                attributes.getAnnotationArray("nested", NestedAnnotation.class);
        assertThat(mirrored).isNotSameAs(original);
        assertThat(mirrored).extracting(NestedAnnotation::value).containsExactly("first", "second");
    }

    @Test
    @SuppressWarnings("deprecation")
    void synthesizesAnnotationsFromAnnotatedElement() {
        Annotation[] annotations = AnnotationUtils.getAnnotations(AnnotatedFixture.class);

        assertThat(annotations).hasSize(1);
        assertThat(annotations[0]).isInstanceOf(ValueAnnotation.class);
        assertThat(((ValueAnnotation) annotations[0]).value()).isEqualTo("spring");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValueAnnotation {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface NestedAnnotation {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface NestedHolder {
        NestedAnnotation[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface AliasedNestedAnnotations {
        @AliasFor("nested")
        NestedAnnotation[] value() default {};

        @AliasFor("value")
        NestedAnnotation[] nested() default {};
    }

    @ValueAnnotation("spring")
    private static final class AnnotatedFixture {}

    @NestedHolder({@NestedAnnotation("first"), @NestedAnnotation("second")})
    private static final class NestedHolderFixture {}
}
