/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.squareup.javapoet$.$AnnotationSpec;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;

public class AutovalueShadedComSquareupJavapoetInnerAnnotationSpecTest {
    @Test
    void getReflectsRuntimeAnnotationMembers() {
        final RichAnnotation annotation = AnnotatedFixture.class.getAnnotation(RichAnnotation.class);

        assertThat(annotation).isNotNull();
        final $AnnotationSpec annotationSpec = $AnnotationSpec.get(annotation, true);

        assertThat(annotationSpec.members).containsOnlyKeys("nested", "priority", "tags", "type", "value");
        assertThat(annotationSpec.members.get("nested")).hasSize(1);
        assertThat(annotationSpec.members.get("priority")).hasSize(1);
        assertThat(annotationSpec.members.get("tags")).hasSize(2);
        assertThat(annotationSpec.members.get("type")).hasSize(1);
        assertThat(annotationSpec.members.get("value")).hasSize(1);
        assertThat(annotationSpec.toString())
                .contains("RichAnnotation")
                .contains("NestedAnnotation")
                .contains("stable")
                .contains("deterministic");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface RichAnnotation {
        String value();

        int priority() default 7;

        Class<?> type();

        NestedAnnotation nested();

        String[] tags();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({})
    public @interface NestedAnnotation {
        String value();
    }

    @RichAnnotation(
            value = "annotation-spec",
            type = String.class,
            nested = @NestedAnnotation("stable"),
            tags = {"runtime", "deterministic"}
    )
    private static final class AnnotatedFixture {
    }
}
