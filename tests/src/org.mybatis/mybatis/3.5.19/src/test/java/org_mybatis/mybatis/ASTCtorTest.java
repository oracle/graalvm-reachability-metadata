/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.util.Map;

import org.apache.ibatis.ognl.Node;
import org.apache.ibatis.ognl.Ognl;
import org.apache.ibatis.ognl.OgnlContext;
import org.apache.ibatis.scripting.xmltags.OgnlCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ASTCtorTest {
    @Test
    void emitsSourceForConstructorArguments() throws Exception {
        Object root = new Object();
        OgnlContext context = Ognl.createDefaultContext(root);
        Node expression = (Node) Ognl.parseExpression("new java.lang.StringBuilder(16)");
        assertTrue(expression.toGetSourceString(context, root).contains("java.lang.StringBuilder(16)"));
        StringBuilder value = (StringBuilder) Ognl.getValue(expression, context, root);
        assertEquals(16, value.capacity());
        assertEquals(StringBuilder.class, context.getCurrentType());
    }

    @Test
    void evaluatesArrayConstructorsWithSizesAndInitializers() {
        assertArrayEquals(new int[3], (int[]) OgnlCache.getValue("new int[3]", Map.of()));
        assertArrayEquals(new String[]{"Ada", "Grace"},
                (String[]) OgnlCache.getValue("new java.lang.String[] {'Ada', 'Grace'}", Map.of()));
    }
}
