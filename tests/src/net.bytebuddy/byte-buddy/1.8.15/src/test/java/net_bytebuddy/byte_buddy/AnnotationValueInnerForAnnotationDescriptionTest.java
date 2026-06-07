/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationValue;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationValueInnerForAnnotationDescriptionTest {
    @Test
    void loadsAnnotationDescriptionWithSuppliedClassLoader() throws Exception {
        AnnotationDescription description = AnnotationDescription.Builder
                .ofType(NestedAnnotation.class)
                .define("value", "byte-buddy")
                .build();
        AnnotationValue<AnnotationDescription, NestedAnnotation> value =
                new AnnotationValue.ForAnnotationDescription<NestedAnnotation>(description);

        AnnotationValue.Loaded<NestedAnnotation> loaded = value.load(getClass().getClassLoader());
        NestedAnnotation annotation = loaded.resolve();

        assertThat(loaded.getState().isResolved()).isTrue();
        assertThat(annotation.annotationType()).isEqualTo(NestedAnnotation.class);
        assertThat(annotation.value()).isEqualTo("byte-buddy");
    }

    @Test
    void resolvesOriginalAnnotationDescription() {
        AnnotationDescription description = AnnotationDescription.Builder
                .ofType(NestedAnnotation.class)
                .define("value", "resolved")
                .build();
        AnnotationValue<AnnotationDescription, Annotation> value =
                new AnnotationValue.ForAnnotationDescription<Annotation>(description);

        AnnotationDescription resolved = value.resolve();

        assertThat(resolved).isEqualTo(description);
        assertThat(resolved.getAnnotationType().represents(NestedAnnotation.class)).isTrue();
    }

    public @interface NestedAnnotation {
        String value();
    }
}
