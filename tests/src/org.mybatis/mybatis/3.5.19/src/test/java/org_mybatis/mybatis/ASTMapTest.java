/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.util.Map;
import java.util.TreeMap;

import org.apache.ibatis.scripting.xmltags.OgnlCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class ASTMapTest {
    @Test
    void createsSpecifiedMapImplementation() {
        Object result = OgnlCache.getValue("#@java.util.TreeMap@{'second': 2, 'first': 1}", Map.of());
        TreeMap<?, ?> map = assertInstanceOf(TreeMap.class, result);
        assertEquals("first", map.firstKey());
        assertEquals(Map.of("first", 1, "second", 2), map);
    }
}
