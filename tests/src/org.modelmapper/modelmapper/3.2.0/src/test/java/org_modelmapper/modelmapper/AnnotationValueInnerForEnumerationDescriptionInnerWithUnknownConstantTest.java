/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.description.annotation.AnnotationValue;
import org.modelmapper.internal.bytebuddy.description.annotation.AnnotationValue.ForEnumerationDescription.WithUnknownConstant;
import org.modelmapper.internal.bytebuddy.description.enumeration.EnumerationDescription;
import org.modelmapper.internal.bytebuddy.description.type.TypeDescription;

public class AnnotationValueInnerForEnumerationDescriptionInnerWithUnknownConstantTest {
    @Test
    void loadsUnresolvedEnumerationValueWithProvidedClassLoader() {
        String missingConstant = "ARCHIVED";
        AnnotationValue<EnumerationDescription, SampleMode> annotationValue = new WithUnknownConstant<>(
            TypeDescription.ForLoadedType.of(SampleMode.class), missingConstant);

        AnnotationValue.Loaded<SampleMode> loadedValue = annotationValue.load(SampleMode.class.getClassLoader());

        assertThat(annotationValue.getState()).isEqualTo(AnnotationValue.State.UNRESOLVED);
        assertThat(loadedValue.getState()).isEqualTo(AnnotationValue.State.UNRESOLVED);
        assertThat(loadedValue.represents(SampleMode.ACTIVE)).isFalse();
        assertThatExceptionOfType(EnumConstantNotPresentException.class)
            .isThrownBy(loadedValue::resolve)
            .withMessageContaining(missingConstant);
    }

    private enum SampleMode {
        ACTIVE
    }
}
