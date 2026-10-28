/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SetFieldInvokerTest {
    @Test
    void writesPublicAndPrivateFieldsThroughMetaObject() {
        Bean bean = new Bean();
        MetaObject meta = SystemMetaObject.forObject(bean);
        meta.setValue("id", 9);
        meta.setValue("name", "Grace");
        assertEquals(9, bean.id);
        assertEquals("Grace", bean.name);
    }

    public static class Bean {
        public int id;
        private String name;
    }
}
