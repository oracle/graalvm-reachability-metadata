/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.description.annotation.AnnotationValue;
import org.modelmapper.internal.bytebuddy.description.type.TypeDescription;

public class AnnotationValueInnerForIncompatibleTypeTest {
    @Test
    void loadsIncompatibleTypeValueWithProvidedClassLoader() {
        AnnotationValue<String, String> annotationValue = new AnnotationValue.ForIncompatibleType<>(
            TypeDescription.ForLoadedType.of(String.class));

        AnnotationValue.Loaded<String> loadedValue = annotationValue.load(String.class.getClassLoader());

        assertThat(loadedValue.toString()).contains(String.class.getName());
        assertThatThrownBy(loadedValue::resolve)
            .isInstanceOf(IncompatibleClassChangeError.class)
            .hasMessageContaining(String.class.toString());
    }
}
