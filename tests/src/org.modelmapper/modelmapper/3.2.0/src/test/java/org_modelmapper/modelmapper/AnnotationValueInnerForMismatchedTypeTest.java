/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.modelmapper.internal.bytebuddy.matcher.ElementMatchers.named;

import java.lang.annotation.AnnotationTypeMismatchException;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.description.annotation.AnnotationValue;
import org.modelmapper.internal.bytebuddy.description.method.MethodDescription;
import org.modelmapper.internal.bytebuddy.description.type.TypeDescription;

public class AnnotationValueInnerForMismatchedTypeTest {
    private static final String MISMATCHED_VALUE = "not-a-boolean";

    @Test
    void loadsMismatchedAnnotationMemberUsingDeclaringTypeAndMethodName() {
        MethodDescription.InDefinedShape annotationMember = TypeDescription.ForLoadedType.of(Deprecated.class)
            .getDeclaredMethods()
            .filter(named("forRemoval"))
            .getOnly();
        AnnotationValue<?, ?> annotationValue = new AnnotationValue.ForMismatchedType<>(
            annotationMember,
            MISMATCHED_VALUE);

        AnnotationValue.Loaded<?> loadedValue = annotationValue.load(Deprecated.class.getClassLoader());

        assertThat(loadedValue.toString()).contains(MISMATCHED_VALUE);
        assertThatThrownBy(loadedValue::resolve)
            .isInstanceOf(AnnotationTypeMismatchException.class)
            .hasMessageContaining(MISMATCHED_VALUE);
    }
}
