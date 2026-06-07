/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic.OfNonGenericType.ForLoadedType;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationDescriptionInnerForLoadedAnnotationTest {
    @Test
    void describesLoadedNestedAnnotationAsAnnotationValue() throws Exception {
        NestedAnnotation annotation = AnnotatedCarrier.class
                .getAnnotation(CarrierAnnotation.class)
                .nested();

        AnnotationValue<?, ?> value = AnnotationDescription.ForLoadedAnnotation.asValue(
                annotation,
                NestedAnnotation.class);

        AnnotationDescription annotationDescription = value.resolve(AnnotationDescription.class);
        AnnotationValue<?, ?> nameValue = annotationDescription.getValue(new MethodDescription.ForLoadedMethod(
                NestedAnnotation.class.getMethod("name")));

        assertThat(annotationDescription.getAnnotationType().represents(NestedAnnotation.class)).isTrue();
        assertThat(nameValue.resolve(String.class)).isEqualTo("inner");
    }

    @Test
    void readsAnnotationPropertyUsingLatentMethodDescription() {
        CarrierAnnotation annotation = AnnotatedCarrier.class.getAnnotation(CarrierAnnotation.class);
        AnnotationDescription description = AnnotationDescription.ForLoadedAnnotation.of(annotation);
        MethodDescription.InDefinedShape property = new MethodDescription.Latent(
                TypeDescription.ForLoadedType.of(CarrierAnnotation.class),
                new MethodDescription.Token(
                        "label",
                        Modifier.PUBLIC | Modifier.ABSTRACT,
                        ForLoadedType.of(String.class)));

        AnnotationValue<?, ?> value = description.getValue(property);

        assertThat(value.resolve(String.class)).isEqualTo("carrier");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface NestedAnnotation {
        String name();

        int count();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface CarrierAnnotation {
        String label();

        NestedAnnotation nested();
    }

    @CarrierAnnotation(label = "carrier", nested = @NestedAnnotation(name = "inner", count = 7))
    private static class AnnotatedCarrier {
    }
}
