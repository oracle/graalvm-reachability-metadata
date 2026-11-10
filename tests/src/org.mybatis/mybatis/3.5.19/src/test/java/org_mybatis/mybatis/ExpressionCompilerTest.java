/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.ognl.ObjectPropertyAccessor;
import org.apache.ibatis.ognl.Ognl;
import org.apache.ibatis.ognl.OgnlContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExpressionCompilerTest {
    @Test
    void choosesThePublicInterfaceForGetterSource() throws Exception {
        Person person = new Person();
        OgnlContext context = Ognl.createDefaultContext(person);
        String source = new ObjectPropertyAccessor().getSourceAccessor(context, person, "name");
        assertEquals(".getName()", source);
        assertEquals(Named.class, context.getCurrentAccessor());
        assertEquals(String.class, context.getCurrentType());
        assertEquals("Ada", Ognl.getValue("name", context, person));
    }

    public interface Named {
        String getName();
    }

    public static class Person implements Named {
        @Override
        public String getName() {
            return "Ada";
        }
    }
}
