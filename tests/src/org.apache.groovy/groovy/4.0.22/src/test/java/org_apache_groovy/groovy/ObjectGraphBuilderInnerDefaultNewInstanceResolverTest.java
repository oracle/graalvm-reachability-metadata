/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.util.ObjectGraphBuilder;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class ObjectGraphBuilderInnerDefaultNewInstanceResolverTest {
    @Test
    void createsDistinctConfiguredGraphRoots() {
        ObjectGraphBuilder builder = new ObjectGraphBuilder();
        builder.setClassNameResolver((ObjectGraphBuilder.ClassNameResolver) name -> Node.class.getName());
        Node first = (Node) builder.invokeMethod("node", new Object[]{new LinkedHashMap<>(Map.of("name", "first"))});
        Node second = (Node) builder.invokeMethod("node", new Object[]{new LinkedHashMap<>(Map.of("name", "second"))});
        assertEquals("first", first.name);
        assertEquals("second", second.name);
        assertNotSame(first, second);
    }

    public static class Node {
        public String name;
    }
}
