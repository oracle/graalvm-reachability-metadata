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

public class AnnotationValueInnerForDescriptionArrayInnerLoadedTest {
    @Test
    void resolvesLoadedTypeDescriptionArray() {
        AnnotationValue<TypeDescription[], Class<?>[]> annotationValue = AnnotationValue.ForDescriptionArray.of(
            new TypeDescription[] {
                TypeDescription.ForLoadedType.of(String.class),
                TypeDescription.ForLoadedType.of(Integer.class)
            });

        AnnotationValue.Loaded<Class<?>[]> loadedValue = annotationValue.load(getClass().getClassLoader());
        Class<?>[] resolvedTypes = loadedValue.resolve();

        assertThat(resolvedTypes).containsExactly(String.class, Integer.class);
    }
}
