/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.util.List;

import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultObjectFactoryTest {
    @Test
    void createsObjectsWithPublicAndPrivateConstructors() {
        DefaultObjectFactory factory = new DefaultObjectFactory();
        assertEquals("default", factory.create(PrivateBean.class).value);
        assertEquals("private", factory.create(PrivateBean.class, List.of(String.class), List.of("private")).value);
        assertEquals(6, factory.create(PublicBean.class, List.of(int.class), List.of(6)).value);
    }

    public static class PrivateBean {
        private final String value;

        private PrivateBean() {
            value = "default";
        }

        private PrivateBean(String value) {
            this.value = value;
        }
    }

    public static class PublicBean {
        private final int value;

        public PublicBean(int value) {
            this.value = value;
        }
    }
}
