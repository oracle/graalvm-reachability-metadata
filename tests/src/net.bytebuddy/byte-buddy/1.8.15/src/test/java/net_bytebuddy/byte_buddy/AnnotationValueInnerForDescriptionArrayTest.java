/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.type.TypeDescription;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class AnnotationValueInnerForDescriptionArrayTest {
    @Test
    void resolvesAndLoadsClassArray() throws Exception {
        TypeDescription[] types = {TypeDescription.ForLoadedType.of(String.class), TypeDescription.ForLoadedType.of(Integer.class)};
        AnnotationValue<TypeDescription[], Class<?>[]> value = AnnotationValue.ForDescriptionArray.of(types);
        assertArrayEquals(types, value.resolve());
        assertArrayEquals(new Class<?>[]{String.class, Integer.class}, value.load(getClass().getClassLoader()).resolve());
    }
}
