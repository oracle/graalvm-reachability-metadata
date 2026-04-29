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
import org.modelmapper.internal.bytebuddy.description.type.TypeDescription;

public class AnnotationValueInnerForDescriptionArrayTest {
    @Test
    void loadsTypeDescriptionArrayComponentType() {
        TypeDescription[] describedTypes = new TypeDescription[] {
            TypeDescription.ForLoadedType.of(String.class),
            TypeDescription.ForLoadedType.of(Integer.class)
        };
        AnnotationValue<TypeDescription[], Class<?>[]> annotationValue = AnnotationValue.ForDescriptionArray.of(
            describedTypes);

        AnnotationValue.Loaded<Class<?>[]> loadedValue = annotationValue.load(getClass().getClassLoader());

        assertThat(loadedValue.getState()).isEqualTo(AnnotationValue.State.RESOLVED);
        assertThat(loadedValue.represents(new Class<?>[] {String.class, Integer.class})).isTrue();
    }
}
