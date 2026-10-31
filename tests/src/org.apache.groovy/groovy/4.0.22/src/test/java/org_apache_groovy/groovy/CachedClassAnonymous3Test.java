/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CachedClassAnonymous3Test {
    @Test
    void discoversInheritedAndDeclaredMethods() {
        Child child = new Child();
        assertEquals("parent", InvokerHelper.invokeMethod(child, "parentName", new Object[]{}));
        assertEquals("child", InvokerHelper.invokeMethod(child, "childName", new Object[]{}));
    }

    public static class Parent {
        public String parentName() {
            return "parent";
        }
    }

    public static class Child extends Parent {
        public String childName() {
            return "child";
        }
    }
}
