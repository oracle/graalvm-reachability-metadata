/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.reflection.property.PropertyCopier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PropertyCopierTest {
    @Test
    void copiesPublicPrivateAndInheritedFields() {
        Bean source = new Bean();
        source.id = 7;
        source.name = "Ada";
        source.setParent("base");
        Bean target = new Bean();
        PropertyCopier.copyBeanProperties(Bean.class, source, target);
        assertEquals(7, target.id);
        assertEquals("Ada", target.name);
        assertEquals("base", target.getParent());
    }

    public static class Parent {
        private String parent;

        public String getParent() {
            return parent;
        }

        public void setParent(String parent) {
            this.parent = parent;
        }
    }

    public static class Bean extends Parent {
        public int id;
        private String name;
    }
}
