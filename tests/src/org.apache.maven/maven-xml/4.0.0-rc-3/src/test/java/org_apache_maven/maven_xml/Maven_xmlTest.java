/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_xml;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.apache.maven.api.xml.XmlNode;
import org.apache.maven.internal.xml.XmlNodeImpl;
import org.apache.maven.internal.xml.XmlNodeStaxBuilder;
import org.apache.maven.internal.xml.XmlNodeWriter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Maven_xmlTest {
    private static final String POM_LIKE_XML = """
            <project xmlns="urn:maven:test" xmlns:x="urn:maven:extension" x:enabled="true">
              <modelVersion> 4.0.0 </modelVersion>
              <name>Maven &amp; XML</name>
              <description xml:space="preserve">  keeps surrounding space  </description>
              <configuration>
                <option key="compiler"><![CDATA[javac --release 21]]></option>
                <empty/>
              </configuration>
            </project>
            """;

    @Test
    void staxBuilderBuildsNamespacedTreeWithAttributesTextCdataAndLocations() throws Exception {
        XmlNode project = parseXml(POM_LIKE_XML);

        assertThat(project.getName()).isEqualTo("project");
        assertThat(project.getNamespaceUri()).isEqualTo("urn:maven:test");
        assertThat(project.getAttribute("xmlns")).isEqualTo("urn:maven:test");
        assertThat(project.getAttribute("xmlns:x")).isEqualTo("urn:maven:extension");
        assertThat(project.getAttribute("x:enabled")).isEqualTo("true");
        assertThat((String) project.getInputLocation()).matches("\\d+:\\d+");

        assertThat(project.getChild("modelVersion").getValue()).isEqualTo("4.0.0");
        assertThat(project.getChild("name").getValue()).isEqualTo("Maven & XML");
        assertThat(project.getChild("description").getValue()).isEqualTo("  keeps surrounding space  ");

        XmlNode configuration = project.getChild("configuration");
        assertThat(configuration.getChildren()).extracting(XmlNode::getName).containsExactly("option", "empty");
        assertThat(configuration.getChild("option").getAttribute("key")).isEqualTo("compiler");
        assertThat(configuration.getChild("option").getValue()).isEqualTo("javac --release 21");
        assertThat(configuration.getChild("empty").getValue()).isIn(null, "");
    }

    @Test
    void nodeImplementationCopiesInputsExposesImmutableViewsAndFindsLastChildByName() {
        Map<String, String> attributes = linkedMap("role", "dominant");
        List<XmlNode> children = new ArrayList<>();
        children.add(node("duplicate", "first"));
        children.add(node("duplicate", "last"));
        Object location = "settings.xml:7";

        XmlNodeImpl root = new XmlNodeImpl("root", "value", attributes, children, location);
        attributes.put("role", "mutated");
        children.add(node("added-after-construction", "ignored"));

        assertThat(root.getName()).isEqualTo("root");
        assertThat(root.getValue()).isEqualTo("value");
        assertThat(root.getAttribute("role")).isEqualTo("dominant");
        assertThat(root.getChild("duplicate").getValue()).isEqualTo("last");
        assertThat(root.getChildCount()).isEqualTo(2);
        assertThat(root.getInputLocation()).isSameAs(location);

        assertThatThrownBy(() -> root.getAttributes().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> root.getChildren().add(node("new-child", "value")))
                .isInstanceOf(UnsupportedOperationException.class);

        XmlNodeImpl renamed = new XmlNodeImpl(root, "renamedRoot");
        assertThat(renamed.getName()).isEqualTo("renamedRoot");
        assertThat(renamed.getValue()).isEqualTo(root.getValue());
        assertThat(renamed.getAttributes()).isEqualTo(root.getAttributes());
        assertThat(renamed.getChildren()).isEqualTo(root.getChildren());
    }

    @Test
    void writerEscapesTextAndAttributesAndRoundTripsThroughStaxBuilder() throws Exception {
        XmlNode document = node(
                "configuration",
                null,
                linkedMap("mode", "strict & safe", "enabled", "true"),
                node("path", "src/main/java & src/test/java"),
                node("pattern", "<includes>*.java</includes>"));

        StringWriter writer = new StringWriter();
        XmlNodeWriter.write(writer, document);
        String xml = writer.toString();

        assertThat(xml).contains("strict &amp; safe");
        assertThat(xml).contains("src/main/java &amp; src/test/java");
        assertThat(xml).contains("&lt;includes>*.java&lt;/includes>");

        XmlNode reparsed = parseXml(xml);
        assertThat(reparsed.getName()).isEqualTo("configuration");
        assertThat(reparsed.getAttribute("mode")).isEqualTo("strict & safe");
        assertThat(reparsed.getChild("path").getValue()).isEqualTo("src/main/java & src/test/java");
        assertThat(reparsed.getChild("pattern").getValue()).isEqualTo("<includes>*.java</includes>");
        assertThat(document.toString()).contains("configuration").contains("path");
    }

    @Test
    void mergeMatchesRepeatedChildrenUsingConfiguredKeyElements() {
        XmlNode dominant = node(
                "dependencies",
                null,
                dependency("org.example", "application", node("version", "2.0"), node("scope", "runtime")),
                dependency("org.example", "local-only", node("version", "1.0")));
        XmlNode recessive = node(
                "dependencies",
                null,
                linkedMap(XmlNode.KEYS_COMBINATION_MODE_ATTRIBUTE, "groupId,artifactId"),
                dependency("org.example", "application", node("version", "1.0"), node("optional", "true")));

        XmlNode merged = XmlNode.merge(dominant, recessive);

        assertThat(merged.getChildren()).extracting(XmlNode::getName)
                .containsExactly("dependency", "dependency");

        XmlNode application = dependencyWithCoordinates(merged, "org.example", "application");
        assertThat(application.getChildren()).extracting(XmlNode::getName)
                .containsExactly("groupId", "artifactId", "version", "scope", "optional");
        assertThat(application.getChild("version").getValue()).isEqualTo("2.0");
        assertThat(application.getChild("scope").getValue()).isEqualTo("runtime");
        assertThat(application.getChild("optional").getValue()).isEqualTo("true");

        XmlNode localOnly = dependencyWithCoordinates(merged, "org.example", "local-only");
        assertThat(localOnly.getChild("version").getValue()).isEqualTo("1.0");
    }

    @Test
    void mergeCombinesAttributesAndChildrenWhileKeepingDominantValues() {
        XmlNode dominant = node(
                "configuration",
                null,
                linkedMap(XmlNode.CHILDREN_COMBINATION_MODE_ATTRIBUTE, XmlNode.CHILDREN_COMBINATION_MERGE,
                        "mode", ""),
                node("feature", "fast", linkedMap("enabled", "true")),
                node("localOnly", "present"));
        XmlNode recessive = node(
                "configuration",
                "ignored-parent-value",
                linkedMap("mode", "fallback", "inherited", "yes"),
                node("feature", "safe", linkedMap("origin", "parent")),
                node("fromParent", "added"));

        XmlNode merged = XmlNode.merge(dominant, recessive);

        assertThat(merged).isNotSameAs(dominant);
        assertThat(merged.getValue()).isNull();
        assertThat(merged.getAttribute(XmlNode.CHILDREN_COMBINATION_MODE_ATTRIBUTE))
                .isEqualTo(XmlNode.CHILDREN_COMBINATION_MERGE);
        assertThat(merged.getAttribute("mode")).isEqualTo("fallback");
        assertThat(merged.getAttribute("inherited")).isEqualTo("yes");
        assertThat(merged.getChildren()).extracting(XmlNode::getName)
                .containsExactly("feature", "localOnly", "fromParent");

        XmlNode mergedFeature = merged.getChild("feature");
        assertThat(mergedFeature.getValue()).isEqualTo("fast");
        assertThat(mergedFeature.getAttribute("enabled")).isEqualTo("true");
        assertThat(mergedFeature.getAttribute("origin")).isEqualTo("parent");
    }

    @Test
    void mergeHonorsCombinationModesForOverrideAppendAndIds() {
        XmlNode override = node(
                "configuration",
                null,
                linkedMap(XmlNode.SELF_COMBINATION_MODE_ATTRIBUTE, XmlNode.SELF_COMBINATION_OVERRIDE),
                node("local", "kept"));
        assertThat(XmlNode.merge(override, node("configuration", null, node("parent", "ignored"))))
                .isSameAs(override);

        XmlNode appendDominant = node(
                "configuration",
                null,
                linkedMap(XmlNode.CHILDREN_COMBINATION_MODE_ATTRIBUTE, XmlNode.CHILDREN_COMBINATION_APPEND),
                node("local", "dominant"));
        XmlNode appendMerged = XmlNode.merge(
                appendDominant,
                node("configuration", null, node("parentOne", "one"), node("parentTwo", "two")));
        assertThat(appendMerged.getChildren()).extracting(XmlNode::getName)
                .containsExactly("parentOne", "parentTwo", "local");

        XmlNode dominantServers = node(
                "servers",
                null,
                server("prod", node("url", "https://prod.example")),
                server("dev", node("url", "https://dev.example")));
        XmlNode recessiveServers = node(
                "servers",
                null,
                server("prod", node("username", "ci-user")));

        XmlNode idMerged = XmlNode.merge(dominantServers, recessiveServers);

        XmlNode prod = childWithAttribute(idMerged, "server", XmlNode.ID_COMBINATION_MODE_ATTRIBUTE, "prod");
        XmlNode dev = childWithAttribute(idMerged, "server", XmlNode.ID_COMBINATION_MODE_ATTRIBUTE, "dev");
        assertThat(prod.getChildren()).extracting(XmlNode::getName).containsExactly("url", "username");
        assertThat(prod.getChild("url").getValue()).isEqualTo("https://prod.example");
        assertThat(prod.getChild("username").getValue()).isEqualTo("ci-user");
        assertThat(dev.getChildren()).extracting(XmlNode::getName).containsExactly("url");
    }

    @Test
    void mergeRemovesDominantChildrenMarkedWithRemoveCombinationMode() {
        XmlNode dominant = node(
                "configuration",
                null,
                node("inherited", null,
                        linkedMap(XmlNode.SELF_COMBINATION_MODE_ATTRIBUTE, XmlNode.SELF_COMBINATION_REMOVE)),
                node("local", "kept"));
        XmlNode recessive = node(
                "configuration",
                null,
                node("inherited", "from-parent"),
                node("parentOnly", "added"));

        XmlNode merged = XmlNode.merge(dominant, recessive);

        assertThat(merged.getChildren()).extracting(XmlNode::getName).containsExactly("local", "parentOnly");
        assertThat(merged.getChild("inherited")).isNull();
        assertThat(merged.getChild("local").getValue()).isEqualTo("kept");
        assertThat(merged.getChild("parentOnly").getValue()).isEqualTo("added");
    }

    private static XmlNode parseXml(String xml) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
        try {
            return XmlNodeStaxBuilder.build(reader, true, streamReader -> streamReader.getLocation().getLineNumber()
                    + ":" + streamReader.getLocation().getColumnNumber());
        } finally {
            reader.close();
        }
    }

    private static XmlNodeImpl server(String id, XmlNode... children) {
        return node("server", null, linkedMap(XmlNode.ID_COMBINATION_MODE_ATTRIBUTE, id), children);
    }

    private static XmlNodeImpl dependency(String groupId, String artifactId, XmlNode... children) {
        List<XmlNode> dependencyChildren = new ArrayList<>();
        dependencyChildren.add(node("groupId", groupId));
        dependencyChildren.add(node("artifactId", artifactId));
        dependencyChildren.addAll(Arrays.asList(children));
        return new XmlNodeImpl("dependency", null, Collections.emptyMap(), dependencyChildren, null);
    }

    private static XmlNodeImpl node(String name, String value, XmlNode... children) {
        return node(name, value, Collections.emptyMap(), children);
    }

    private static XmlNodeImpl node(String name, String value, Map<String, String> attributes, XmlNode... children) {
        return new XmlNodeImpl(name, value, attributes, Arrays.asList(children), null);
    }

    private static Map<String, String> linkedMap(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static XmlNode childWithAttribute(XmlNode parent, String name, String attribute, String value) {
        return parent.getChildren().stream()
                .filter(child -> name.equals(child.getName()))
                .filter(child -> value.equals(child.getAttribute(attribute)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing child " + name + " with " + attribute + "=" + value));
    }

    private static XmlNode dependencyWithCoordinates(XmlNode parent, String groupId, String artifactId) {
        return parent.getChildren().stream()
                .filter(child -> "dependency".equals(child.getName()))
                .filter(child -> groupId.equals(child.getChild("groupId").getValue()))
                .filter(child -> artifactId.equals(child.getChild("artifactId").getValue()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing dependency " + groupId + ":" + artifactId));
    }
}
