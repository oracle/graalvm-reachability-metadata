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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ObjectGraphBuilderInnerObjectFactoryTest {
    @Test
    void resolvesAConfiguredGraphNodeClassByName() {
        ObjectGraphBuilder builder = new ObjectGraphBuilder();
        builder.setClassLoader(Node.class.getClassLoader());
        builder.setClassNameResolver((ObjectGraphBuilder.ClassNameResolver) name -> Node.class.getName());
        Node node = (Node) builder.invokeMethod("node", new Object[]{new LinkedHashMap<>(Map.of("size", 5))});
        assertEquals(5, node.size);
    }

    @Test
    void rejectsANodeWhoseConfiguredClassCannotBeResolved() {
        ObjectGraphBuilder builder = new ObjectGraphBuilder();
        builder.setClassNameResolver((ObjectGraphBuilder.ClassNameResolver)
                name -> "org_apache_groovy.groovy.UnavailableGraphNode");
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> builder.invokeMethod("node", new Object[0]));
        ClassNotFoundException cause = assertInstanceOf(ClassNotFoundException.class,
                failure.getCause().getCause());
        assertEquals("org_apache_groovy.groovy.UnavailableGraphNode", cause.getMessage());
    }

    public static class Node {
        public int size;
    }
}
