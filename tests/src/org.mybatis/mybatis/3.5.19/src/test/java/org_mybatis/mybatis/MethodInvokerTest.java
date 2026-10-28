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

public class MethodInvokerTest {
    @Test
    void accessesPublicAndPrivateBeanMethods() {
        Bean bean = new Bean();
        MetaObject meta = SystemMetaObject.forObject(bean);
        meta.setValue("name", "Ada");
        meta.setValue("secret", "Grace");
        assertEquals("Ada", meta.getValue("name"));
        assertEquals("Grace", meta.getValue("secret"));
        assertEquals("Grace", bean.secret);
    }

    public static class Bean {
        private String name;
        private String secret;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        private String getSecret() {
            return secret;
        }

        private void setSecret(String secret) {
            this.secret = secret;
        }
    }
}
