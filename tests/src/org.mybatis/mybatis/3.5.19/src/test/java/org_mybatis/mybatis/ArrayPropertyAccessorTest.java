/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.util.Map;

import org.apache.ibatis.scripting.xmltags.OgnlCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class ArrayPropertyAccessorTest {
    @Test
    void copiesArrayUsingAllElementsSubscript() {
        int[] input = {3, 7, 9};
        Map<String, Object> root = Map.of("numbers", input);
        int[] copy = (int[]) OgnlCache.getValue("numbers[*]", root);
        assertArrayEquals(input, copy);
        assertNotSame(input, copy);
        assertEquals(9, OgnlCache.getValue("numbers[$]", root));
    }
}
