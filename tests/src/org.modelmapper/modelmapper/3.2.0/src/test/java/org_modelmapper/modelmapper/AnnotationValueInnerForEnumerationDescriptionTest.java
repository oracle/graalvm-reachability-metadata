/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.description.annotation.AnnotationValue;
import org.modelmapper.internal.bytebuddy.description.enumeration.EnumerationDescription;

public class AnnotationValueInnerForEnumerationDescriptionTest {
    @Test
    void loadsEnumerationDescriptionWithProvidedClassLoader() {
        EnumerationDescription enumerationDescription = new EnumerationDescription.ForLoadedEnumeration(SampleMode.ACTIVE);
        AnnotationValue<EnumerationDescription, SampleMode> annotationValue = AnnotationValue.ForEnumerationDescription.of(
            enumerationDescription);

        AnnotationValue.Loaded<SampleMode> loadedValue = annotationValue.load(SampleMode.class.getClassLoader());

        assertThat(loadedValue.getState()).isEqualTo(AnnotationValue.State.RESOLVED);
        assertThat(loadedValue.resolve()).isSameAs(SampleMode.ACTIVE);
        assertThat(loadedValue.represents(SampleMode.ACTIVE)).isTrue();
    }

    private enum SampleMode {
        ACTIVE
    }
}
