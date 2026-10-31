/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultTypeTransformationTest {
    @Test
    void convertsTrailingArgumentsToTheDeclaredVarargsType() {
        assertArrayEquals(new String[0], (String[]) DefaultTypeTransformation.castToVargsArray(
                new Object[]{"prefix"}, 1, String[].class));
        assertArrayEquals(new int[]{3, 5}, (int[]) DefaultTypeTransformation.castToVargsArray(
                new Object[]{"prefix", 3, 5}, 1, int[].class));
    }

    @Test
    void convertsPrimitiveArraysAndCreatesConcreteCollections() {
        assertArrayEquals(new Integer[]{3, 5}, DefaultTypeTransformation.primitiveArrayBox(new int[]{3, 5}));
        assertArrayEquals(new String[]{"red", "blue"},
                (String[]) DefaultTypeTransformation.castToType(List.of("red", "blue"), String[].class));
        ArrayDeque<?> queue = (ArrayDeque<?>) DefaultTypeTransformation.castToType(new int[]{3, 5}, ArrayDeque.class);
        assertEquals(List.of(3, 5), List.copyOf(queue));
    }
}
