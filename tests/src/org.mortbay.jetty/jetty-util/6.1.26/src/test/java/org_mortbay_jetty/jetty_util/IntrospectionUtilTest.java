/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.mortbay.util.IntrospectionUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IntrospectionUtilTest {
    @Test
    public void findsDeclaredAndInheritedBeanMembers() throws Exception {
        Method setter = IntrospectionUtil.findMethod(Child.class, "setLabel", new Class<?>[]{String.class}, false, true);
        assertEquals(Child.class, setter.getDeclaringClass());
        assertTrue(IntrospectionUtil.isJavaBeanCompliantSetter(setter));
        Method inherited = IntrospectionUtil.findMethod(Child.class, "setCount", new Class<?>[]{Integer.class}, true, false);
        assertEquals(Parent.class, inherited.getDeclaringClass());
        assertEquals("setCount", inherited.getName());
        Field local = IntrospectionUtil.findField(Child.class, "label", String.class, false, true);
        assertEquals(Child.class, local.getDeclaringClass());
        Field inheritedField = IntrospectionUtil.findField(Child.class, "count", Number.class, true, true);
        assertEquals(Parent.class, inheritedField.getDeclaringClass());
        assertEquals(Number.class, inheritedField.getType());
    }

    @Test
    public void comparesMemberNamesAndSignatures() throws Exception {
        Method setter = IntrospectionUtil.findMethod(Child.class, "setLabel", new Class<?>[]{String.class}, false, true);
        assertTrue(IntrospectionUtil.containsSameMethodSignature(setter, Peer.class, true));
        assertFalse(IntrospectionUtil.containsSameMethodSignature(setter, Parent.class, false));
        Field label = IntrospectionUtil.findField(Child.class, "label", String.class, false, true);
        assertTrue(IntrospectionUtil.containsSameFieldName(label, Peer.class, true));
        assertFalse(IntrospectionUtil.containsSameFieldName(label, Parent.class, false));
    }

    public static class Parent {
        public Number count;

        public void setCount(Number count) {
            this.count = count;
        }
    }

    public static class Child extends Parent {
        public String label;

        public void setLabel(String label) {
            this.label = label;
        }
    }

    public static class Peer {
        public String label;

        public void setLabel(String label) {
            this.label = label;
        }
    }
}
