/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.util.Map;

import org.apache.ibatis.ognl.ASTStaticField;
import org.apache.ibatis.ognl.Ognl;
import org.apache.ibatis.ognl.OgnlContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ASTStaticFieldTest {
    @Test
    void resolvesStaticFieldTypesWhileRenderingSource() throws Exception {
        Map<String, Object> root = Map.of();
        OgnlContext context = Ognl.createDefaultContext(root);
        ASTStaticField expression = (ASTStaticField) Ognl.parseExpression("@java.lang.Integer@MAX_VALUE");
        assertEquals("java.lang.Integer.MAX_VALUE", expression.toGetSourceString(context, root));
        assertEquals(int.class, expression.getGetterClass());
        assertEquals(int.class, context.getCurrentType());
        assertEquals(Integer.MAX_VALUE, Ognl.getValue(expression, context, root));
    }
}
