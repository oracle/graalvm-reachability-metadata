/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.IncompleteAnnotationException;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.description.annotation.AnnotationValue;
import org.modelmapper.internal.bytebuddy.description.type.TypeDescription;

public class AnnotationValueInnerForMissingValueTest {
    private static final String MISSING_PROPERTY = "missingProperty";

    @Test
    void loadsMissingValueForAnnotationType() {
        AnnotationValue<Object, Object> annotationValue = new AnnotationValue.ForMissingValue<>(
            TypeDescription.ForLoadedType.of(Deprecated.class),
            MISSING_PROPERTY);

        AnnotationValue.Loaded<Object> loadedValue = annotationValue.load(Deprecated.class.getClassLoader());

        assertThat(annotationValue.getState()).isEqualTo(AnnotationValue.State.UNDEFINED);
        assertThat(loadedValue.getState()).isEqualTo(AnnotationValue.State.UNDEFINED);
        assertThat(loadedValue.represents(null)).isFalse();
        assertThatThrownBy(loadedValue::resolve)
            .isInstanceOf(IncompleteAnnotationException.class)
            .hasMessageContaining(Deprecated.class.getName())
            .hasMessageContaining(MISSING_PROPERTY);
    }
}
