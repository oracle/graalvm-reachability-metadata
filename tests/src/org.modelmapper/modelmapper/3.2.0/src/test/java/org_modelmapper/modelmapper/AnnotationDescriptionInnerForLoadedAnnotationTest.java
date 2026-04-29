/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.modelmapper.internal.bytebuddy.matcher.ElementMatchers.is;
import static org.modelmapper.internal.bytebuddy.matcher.ElementMatchers.named;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.description.annotation.AnnotationDescription;
import org.modelmapper.internal.bytebuddy.description.annotation.AnnotationValue;
import org.modelmapper.internal.bytebuddy.description.method.MethodDescription;
import org.modelmapper.internal.bytebuddy.description.type.TypeDescription;

public class AnnotationDescriptionInnerForLoadedAnnotationTest {
    private static final String SINCE_VALUE = "modelmapper-dynamic-access";

    @Test
    void convertsLoadedAnnotationIntoAnnotationValue() {
        Deprecated annotation = AnnotatedType.class.getAnnotation(Deprecated.class);

        AnnotationValue<?, ?> annotationValue = AnnotationDescription.ForLoadedAnnotation.asValue(annotation, Deprecated.class);
        Deprecated loadedAnnotation = annotationValue.load(AnnotatedType.class.getClassLoader()).resolve(Deprecated.class);

        assertThat(loadedAnnotation.since()).isEqualTo(SINCE_VALUE);
        assertThat(loadedAnnotation.forRemoval()).isFalse();
    }

    @Test
    void readsLoadedAnnotationMemberFromLatentMethodDescription() {
        Deprecated annotation = AnnotatedType.class.getAnnotation(Deprecated.class);
        AnnotationDescription.Loadable<Deprecated> annotationDescription = AnnotationDescription.ForLoadedAnnotation.of(annotation);
        MethodDescription.InDefinedShape sinceMethod = latentAnnotationMethod(Deprecated.class, "since");

        AnnotationValue<?, ?> sinceValue = annotationDescription.getValue(sinceMethod);

        assertThat(sinceValue.resolve(String.class)).isEqualTo(SINCE_VALUE);
    }

    private static MethodDescription.InDefinedShape latentAnnotationMethod(Class<?> annotationType, String methodName) {
        TypeDescription typeDescription = TypeDescription.ForLoadedType.of(annotationType);
        MethodDescription.InDefinedShape loadedMethod = typeDescription.getDeclaredMethods()
            .filter(named(methodName))
            .getOnly();
        return new MethodDescription.Latent(typeDescription, loadedMethod.asToken(is(typeDescription)));
    }

    @Deprecated(since = SINCE_VALUE)
    private static final class AnnotatedType {
    }
}
