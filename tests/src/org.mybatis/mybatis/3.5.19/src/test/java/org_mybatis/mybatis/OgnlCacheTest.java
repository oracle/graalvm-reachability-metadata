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

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OgnlCacheTest {
    @Test
    void discoversConstructorsForExpressionEvaluation() {
        Object result = OgnlCache.getValue("new java.lang.String('cached')", Map.of());
        assertEquals("cached", result);
        assertEquals("cached", OgnlCache.getValue("new java.lang.String('cached')", Map.of()));
    }
}
