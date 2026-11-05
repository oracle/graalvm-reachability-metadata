/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.Closure;
import groovy.util.ObjectGraphBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ObjectGraphBuilderInnerReflectionClassNameResolverTest {
    @Test
    void infersChildClassesFromFieldsAndCollectionElementTypes() {
        ObjectGraphBuilder builder = new ObjectGraphBuilder();
        ObjectGraphBuilder.ReflectionClassNameResolver resolver = builder.new ReflectionClassNameResolver("org_apache_groovy.groovy");
        builder.setClassNameResolver((ObjectGraphBuilder.ClassNameResolver) name ->
                "root".equals(name) ? Root.class.getName() : resolver.resolveClassname(name));
        Root root = (Root) builder.invokeMethod("root", new Object[]{new Closure<Object>(this) {
            public Object doCall() {
                builder.invokeMethod("child", new Object[]{new LinkedHashMap<>(Map.of("name", "single"))});
                builder.invokeMethod("children", new Object[]{new LinkedHashMap<>(Map.of("name", "listed"))});
                return null;
            }
        }});
        assertEquals("single", root.child.name);
        assertEquals("listed", root.children.get(0).name);
    }

    public static class Root {
        public Child child;
        public List<Child> children = new ArrayList<>();
    }

    public static class Child {
        public String name;
    }
}
