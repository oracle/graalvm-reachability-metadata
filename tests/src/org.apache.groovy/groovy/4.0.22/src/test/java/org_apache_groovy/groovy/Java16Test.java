/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.vmplugin.VMPluginFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Java16Test {
    @Test
    void invokesTheSuperclassImplementationThroughTheVmPlugin() throws Throwable {
        Method method = Parent.class.getMethod("greeting");
        Object handle = VMPluginFactory.getPlugin().getInvokeSpecialHandle(method, new Child());
        assertEquals("parent", VMPluginFactory.getPlugin().invokeHandle(handle, new Object[]{}));
    }

    public static class Parent {
        public String greeting() {
            return "parent";
        }
    }

    public static class Child extends Parent {
        @Override
        public String greeting() {
            return "child";
        }
    }

    @Test
    void exposesRecordComponentNamesAndTypes() {
        assertEquals(List.of("name", "quantity"), VMPluginFactory.getPlugin().getRecordComponentNames(Line.class));
        ClassNode node = ClassHelper.make(Line.class);
        assertEquals(List.of("name", "quantity"), node.getRecordComponents().stream().map(component -> component.getName()).toList());
        assertEquals(String.class, node.getRecordComponents().get(0).getType().getTypeClass());
        assertEquals(int.class, node.getRecordComponents().get(1).getType().getTypeClass());
    }

    @Test
    void invokesAPublicMethodFromANonOpenJdkModule() throws Throwable {
        Method method = CharSequence.class.getMethod("length");
        Object handle = VMPluginFactory.getPlugin().getInvokeSpecialHandle(method, "parcel");
        assertEquals(6, VMPluginFactory.getPlugin().invokeHandle(handle, new Object[]{}));
    }

    public record Line(String name, int quantity) {
    }
}
