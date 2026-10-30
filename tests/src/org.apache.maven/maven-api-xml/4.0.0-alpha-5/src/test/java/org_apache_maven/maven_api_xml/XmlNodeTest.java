/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_xml;

import org.apache.maven.api.xml.XmlNode;
import org.apache.maven.internal.xml.XmlNodeImpl;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class XmlNodeTest {
    @Test
    void readsImmutableConfigurationTree() {
        XmlNode first = new XmlNodeImpl("option", "first");
        XmlNode last = new XmlNodeImpl("option", "last");
        Map<String, String> attributes = new HashMap<>(Map.of("implementation", "compiler"));
        List<XmlNode> children = new ArrayList<>(List.of(first, last));
        Object location = "build configuration";
        XmlNode node = new XmlNodeImpl("configuration", null, attributes, children, location);

        attributes.put("implementation", "changed");
        children.clear();

        assertEquals("configuration", node.getName());
        assertNull(node.getValue());
        assertEquals(Map.of("implementation", "compiler"), node.getAttributes());
        assertEquals("compiler", node.getAttribute("implementation"));
        assertNull(node.getAttribute("missing"));
        assertEquals(List.of(first, last), node.getChildren());
        assertSame(last, node.getChild("option"));
        assertEquals("last", node.getChild("option").getValue());
        assertNull(node.getChild("missing"));
        assertSame(location, node.getInputLocation());
        assertEquals(node, node.clone());
        assertThrows(UnsupportedOperationException.class, () -> node.getAttributes().put("key", "value"));
        assertThrows(UnsupportedOperationException.class, () -> node.getChildren().add(first));
    }

    @Test
    void handlesAbsentNodesInStaticMergeOverloads() {
        XmlNode node = new XmlNodeImpl("setting", "enabled");

        assertNull(XmlNode.merge(null, (XmlNode) null));
        assertNull(XmlNode.merge(null, null, Boolean.TRUE));
        assertSame(node, XmlNode.merge(node, (XmlNode) null));
        assertSame(node, XmlNode.merge(null, node));
        assertSame(node, XmlNode.merge(node, null, Boolean.FALSE));
        assertSame(node, XmlNode.merge(null, node, Boolean.TRUE));
        assertSame(node, node.merge(null));
    }

    @Test
    void mergesDefaultsWithoutChangingOriginalNodes() {
        XmlNode dominantChild = new XmlNodeImpl("compiler", "custom", Map.of("debug", "true"), null, null);
        XmlNode recessiveChild = new XmlNodeImpl("compiler", "default", Map.of("encoding", "UTF-8"), null, null);
        XmlNode additionalChild = new XmlNodeImpl("output", "classes");
        XmlNode dominant = new XmlNodeImpl("configuration", null, Map.of("scope", "local"), List.of(dominantChild), null);
        XmlNode recessive = new XmlNodeImpl("configuration", null, Map.of("scope", "parent", "inherited", "yes"),
                List.of(recessiveChild, additionalChild), null);

        XmlNode merged = XmlNode.merge(dominant, recessive);

        assertEquals("configuration", merged.getName());
        assertEquals(Map.of("scope", "local", "inherited", "yes"), merged.getAttributes());
        assertEquals(2, merged.getChildren().size());
        assertEquals("custom", merged.getChild("compiler").getValue());
        assertEquals(Map.of("debug", "true", "encoding", "UTF-8"), merged.getChild("compiler").getAttributes());
        assertEquals("classes", merged.getChild("output").getValue());
        assertEquals(Map.of("scope", "local"), dominant.getAttributes());
        assertEquals(List.of(dominantChild), dominant.getChildren());
        assertEquals(Map.of("debug", "true"), dominantChild.getAttributes());
        assertEquals("default", recessiveChild.getValue());
    }

    @Test
    void appendsChildrenWhenConfiguredThroughApiConstant() {
        XmlNode local = new XmlNodeImpl("item", "local");
        XmlNode inherited = new XmlNodeImpl("item", "inherited");
        XmlNode dominant = new XmlNodeImpl("items", null,
                Map.of(XmlNode.CHILDREN_COMBINATION_MODE_ATTRIBUTE, XmlNode.CHILDREN_COMBINATION_APPEND),
                List.of(local), null);
        XmlNode recessive = new XmlNodeImpl("items", null, null, List.of(inherited), null);

        XmlNode merged = dominant.merge(recessive);

        assertEquals(List.of(inherited, local), merged.getChildren());
        assertEquals(List.of(local), dominant.getChildren());
        assertEquals(List.of(inherited), recessive.getChildren());
    }

    @Test
    void explicitMergeOverrideTakesPrecedenceOverAppendAttribute() {
        XmlNode local = new XmlNodeImpl("item", "local");
        XmlNode inherited = new XmlNodeImpl("item", "inherited", Map.of("source", "parent"), null, null);
        XmlNode dominant = new XmlNodeImpl("items", null,
                Map.of(XmlNode.CHILDREN_COMBINATION_MODE_ATTRIBUTE, XmlNode.CHILDREN_COMBINATION_APPEND),
                List.of(local), null);
        XmlNode recessive = new XmlNodeImpl("items", null, null, List.of(inherited), null);

        XmlNode merged = XmlNode.merge(dominant, recessive, Boolean.TRUE);

        assertEquals(1, merged.getChildren().size());
        assertEquals("local", merged.getChild("item").getValue());
        assertEquals("parent", merged.getChild("item").getAttribute("source"));
    }

    @Test
    void explicitAppendOverrideKeepsBothMatchingChildren() {
        XmlNode local = new XmlNodeImpl("item", "local");
        XmlNode inherited = new XmlNodeImpl("item", "inherited");
        XmlNode dominant = new XmlNodeImpl("items", null, null, List.of(local), null);
        XmlNode recessive = new XmlNodeImpl("items", null, null, List.of(inherited), null);

        XmlNode merged = dominant.merge(recessive, Boolean.FALSE);

        assertEquals(List.of(inherited, local), merged.getChildren());
    }

    @Test
    void selfOverrideSuppressesInheritedAttributesAndChildren() {
        XmlNode local = new XmlNodeImpl("setting", "local");
        XmlNode dominant = new XmlNodeImpl("configuration", null,
                Map.of(XmlNode.SELF_COMBINATION_MODE_ATTRIBUTE, XmlNode.SELF_COMBINATION_OVERRIDE),
                List.of(local), null);
        XmlNode recessive = new XmlNodeImpl("configuration", null, Map.of("inherited", "yes"),
                List.of(new XmlNodeImpl("output", "classes")), null);

        XmlNode merged = dominant.merge(recessive);

        assertEquals(List.of(local), merged.getChildren());
        assertNull(merged.getAttribute("inherited"));
        assertNull(merged.getChild("output"));
    }

    @Test
    void removesMatchingChildWhenRequestedByDominantConfiguration() {
        XmlNode removed = new XmlNodeImpl("debug", null,
                Map.of(XmlNode.SELF_COMBINATION_MODE_ATTRIBUTE, XmlNode.SELF_COMBINATION_REMOVE), null, null);
        XmlNode retained = new XmlNodeImpl("encoding", "UTF-8");
        XmlNode dominant = new XmlNodeImpl("configuration", null, null, List.of(removed, retained), null);
        XmlNode recessive = new XmlNodeImpl("configuration", null, null,
                List.of(new XmlNodeImpl("debug", "true")), null);

        XmlNode merged = dominant.merge(recessive);

        assertNull(merged.getChild("debug"));
        assertEquals(List.of(retained), merged.getChildren());
        assertEquals(List.of(removed, retained), dominant.getChildren());
    }
}
