/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.vmplugin.v8.Java8;
import org.codehaus.groovy.vmplugin.v9.Java9;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Java8Test {
    @Test
    void modelsJavaFieldsMethodsAndConstructorsAsGroovyAstNodes() {
        ClassNode node = ClassHelper.makeWithoutCaching(Person.class);
        assertEquals(String.class, node.getField("name").getType().getTypeClass());
        assertEquals(1, node.getDeclaredConstructors().size());
        assertTrue(node.getMethods("greeting").stream().anyMatch(method -> method.getParameters().length == 0));
    }

    @Test
    void invokesTheSuperclassImplementationInsteadOfTheOverride() throws Throwable {
        Java8 plugin = new Java9();
        Method method = Parent.class.getMethod("greeting");
        Object handle = plugin.getInvokeSpecialHandle(method, new Child());
        assertEquals("parent", plugin.invokeHandle(handle, new Object[0]));
    }

    @Test
    void invokesAPrivateSuperclassImplementationFromAChildReceiver() throws Throwable {
        Java8 plugin = new Java9();
        Method method = Parent.class.getDeclaredMethod("internalGreeting");
        Object handle = plugin.getInvokeSpecialHandle(method, new Child());
        assertEquals("private parent", plugin.invokeHandle(handle, new Object[0]));
    }

    public static class Parent {
        public String greeting() {
            return "parent";
        }

        private String internalGreeting() {
            return "private parent";
        }
    }

    public static class Child extends Parent {
        @Override
        public String greeting() {
            return "child";
        }
    }

    public static class Person {
        public final String name;

        public Person(String name) {
            this.name = name;
        }

        public String greeting() {
            return "Hello " + name;
        }
    }
}
