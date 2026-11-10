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
import org.apache.ibatis.scripting.xmltags.OgnlCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ObjectPropertyAccessorTest {
    @Test
    void determinesPublicFieldTypeAndEvaluatesBeanAssignment() {
        Bean bean = new Bean();
        ObjectPropertyAccessor accessor = new ObjectPropertyAccessor();
        assertEquals(String.class, accessor.getPropertyClass(null, bean, "name"));
        assertEquals("Ada", OgnlCache.getValue("name = 'Ada'", bean));
        assertEquals("Ada", bean.name);
    }

    @Test
    void emitsPublicFieldAccessorSource() {
        Bean bean = new Bean();
        OgnlContext context = Ognl.createDefaultContext(bean);
        ObjectPropertyAccessor accessor = new ObjectPropertyAccessor();
        assertEquals(".name", accessor.getSourceAccessor(context, bean, "name"));
        assertEquals(String.class, context.getCurrentType());
        assertEquals(Bean.class, context.getCurrentAccessor());
    }

    @Test
    void assignsThroughANamedWriterMethod() throws Exception {
        Bean bean = new Bean();
        Ognl.setValue("rename", Ognl.createDefaultContext(bean), bean, "Grace");
        assertEquals("Grace", bean.name);
    }

    public static class Bean {
        public String name;

        public void rename(String value) {
            name = value;
        }
    }
}
