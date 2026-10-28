/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GetFieldInvokerTest {
    @Test
    void readsPublicAndPrivateFieldsThroughMetaObject() {
        Bean bean = new Bean();
        assertEquals(8, SystemMetaObject.forObject(bean).getValue("id"));
        assertEquals("Ada", SystemMetaObject.forObject(bean).getValue("name"));
    }

    public static class Bean {
        public int id = 8;
        private String name = "Ada";
    }
}
