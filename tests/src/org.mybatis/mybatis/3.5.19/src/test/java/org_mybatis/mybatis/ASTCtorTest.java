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

public class ASTCtorTest {
    @Test
    void evaluatesArrayConstructorsWithSizesAndInitializers() {
        assertArrayEquals(new int[3], (int[]) OgnlCache.getValue("new int[3]", Map.of()));
        assertArrayEquals(new String[]{"Ada", "Grace"},
                (String[]) OgnlCache.getValue("new java.lang.String[] {'Ada', 'Grace'}", Map.of()));
    }
}
