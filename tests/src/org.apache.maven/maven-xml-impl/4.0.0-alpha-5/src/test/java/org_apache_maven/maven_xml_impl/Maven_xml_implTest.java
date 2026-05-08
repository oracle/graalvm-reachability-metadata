/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_xml_impl;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.maven.api.xml.XmlNode;
import org.apache.maven.internal.xml.XmlNodeBuilder;
import org.apache.maven.internal.xml.XmlNodeImpl;
import org.apache.maven.internal.xml.XmlNodeWriter;
import org.apache.maven.internal.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.MXParser;
import org.codehaus.plexus.util.xml.pull.XmlPullParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Maven_xml_implTest {
    @Test
    void parsesXmlNodesWithAttributesLocationsAndWhitespaceRules() throws Exception {
        String xml = """
                <configuration enabled="true">
                  <description>  trimmed value  </description>
                  <script xml:space="preserve">  keep spaces  </script>
                  <empty></empty>
                  <selfClosing/>
                  <item id="first">one</item>
                  <item id="second">two</item>
                </configuration>
                """;

        XmlNode root = XmlNodeBuilder.build(
                new StringReader(xml), parser -> parser.getName() + "@" + parser.getLineNumber());

        assertThat(root.getName()).isEqualTo("configuration");
        assertThat(root.getAttribute("enabled")).isEqualTo("true");
        assertThat(String.valueOf(root.getInputLocation())).startsWith("configuration@");
        assertThat(root.getChildren()).extracting(XmlNode::getName)
                .containsExactly("description", "script", "empty", "selfClosing", "item", "item");
        assertThat(root.getChild("description").getValue()).isEqualTo("trimmed value");
        assertThat(root.getChild("script").getValue()).isEqualTo("  keep spaces  ");
        assertThat(root.getChild("script").getAttributes()).containsEntry("xml:space", "preserve");
        assertThat(root.getChild("empty").getValue()).isEmpty();
        assertThat(root.getChild("selfClosing").getValue()).isNull();
        assertThat(root.getChild("item").getAttribute("id")).isEqualTo("second");
        assertThat(root.getChild(null)).isNull();
    }

    @Test
    void supportsInputStreamParserAndUntrimmedTextBuildPaths() throws Exception {
        String xml = "<root><value>  not trimmed  </value></root>";
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);

        XmlNode fromStream = XmlNodeBuilder.build(
                new ByteArrayInputStream(bytes), StandardCharsets.UTF_8.name(), false);
        MXParser parser = new MXParser();
        parser.setInput(new StringReader(xml));
        XmlNode fromParser = XmlNodeBuilder.build(parser, false);

        assertThat(fromStream).isEqualTo(fromParser);
        assertThat(fromStream.getChild("value").getValue()).isEqualTo("  not trimmed  ");
    }

    @Test
    void writesEscapedAndUnescapedXmlThroughWriterPrintWriterAndXmlWriter() throws Exception {
        XmlNode root = new XmlNodeImpl(
                "root",
                null,
                Map.of("flag", "a&b"),
                List.of(new XmlNodeImpl("message", "<hello>&goodbye</hello>")),
                null);

        StringWriter writerOutput = new StringWriter();
        XmlNodeWriter.write(writerOutput, root);
        String escapedXml = writerOutput.toString();

        StringWriter printWriterOutput = new StringWriter();
        XmlNodeWriter.write(new PrintWriter(printWriterOutput), root);

        StringWriter xmlWriterOutput = new StringWriter();
        XmlNodeWriter.write(new PrettyPrintXMLWriter(xmlWriterOutput), root, false);

        assertThat(printWriterOutput.toString()).isEqualTo(escapedXml);
        assertThat(escapedXml).contains("flag=\"a&amp;b\"");
        assertThat(escapedXml).contains("&lt;hello&gt;&amp;goodbye&lt;/hello&gt;");
        assertThat(xmlWriterOutput.toString()).contains("<hello>&goodbye</hello>");
        assertThat(root.toString()).isEqualTo(escapedXml);
        assertThat(((XmlNodeImpl) root).toUnescapedString()).contains("<hello>&goodbye</hello>");
    }

    @Test
    void mergesXmlNodesUsingDominanceAppendIdKeysAndRemovalRules() throws Exception {
        XmlNode dominant = XmlNodeBuilder.build(new StringReader("""
                <configuration combine.keys="scope">
                  <property scope="compile"><name>compiler</name><value>javac</value></property>
                  <property combine.id="same"><name>dominant</name><value>kept</value></property>
                  <property combine.self="remove"><name>removed</name></property>
                </configuration>
                """), fixedLocation("dominant"));
        XmlNode recessive = XmlNodeBuilder.build(new StringReader("""
                <configuration combine.keys="scope" inherited="true">
                  <property scope="compile"><name>compiler</name><value>ecj</value><fallback>yes</fallback></property>
                  <property scope="test"><name>tests</name><value>surefire</value></property>
                  <property combine.id="same"><name>recessive</name><value>overridden</value></property>
                  <property><name>removed</name><value>not copied</value></property>
                </configuration>
                """), fixedLocation("recessive"));

        XmlNode merged = XmlNodeImpl.merge(dominant, recessive, true);

        assertThat(merged.getAttribute("inherited")).isEqualTo("true");
        assertThat(merged.getChildren()).hasSize(3);
        assertThat(merged.getChildren()).extracting(XmlNode::getInputLocation)
                .containsExactly("dominant", "dominant", "recessive");
        assertThat(merged.getChildren()).extracting(child -> child.getChild("name").getValue())
                .containsExactly("compiler", "dominant", "tests");
        assertThat(merged.getChildren().get(0).getChild("value").getValue()).isEqualTo("javac");
        assertThat(merged.getChildren().get(0).getChild("fallback").getValue()).isEqualTo("yes");
        assertThat(XmlNode.merge(null, recessive)).isSameAs(recessive);
        assertThat(XmlNode.merge(dominant, (XmlNode) null)).isSameAs(dominant);
    }

    @Test
    void appendMergeModePrependsRecessiveChildrenUnlessOverrideForcesDeepMerge() throws Exception {
        XmlNode dominant = XmlNodeBuilder.build(new StringReader("""
                <configuration><items combine.children="append"><item>child</item></items></configuration>
                """));
        XmlNode recessive = XmlNodeBuilder.build(new StringReader("""
                <configuration><items><item>parent</item></items></configuration>
                """));

        XmlNode appended = dominant.merge(recessive);
        XmlNode forcedMerge = dominant.merge(recessive, Boolean.TRUE);

        assertThat(appended.getChild("items").getChildren()).extracting(XmlNode::getValue)
                .containsExactly("parent", "child");
        assertThat(forcedMerge.getChild("items").getChildren()).extracting(XmlNode::getValue)
                .containsExactly("child");
    }

    @Test
    void mutatesXpp3DomTreeThroughPublicAdapterMethods() {
        Xpp3Dom root = new Xpp3Dom("root", "root-location");
        root.setAttribute("one", "1");
        root.setAttribute("two", "2");
        root.setValue("initial");
        Xpp3Dom child = new Xpp3Dom("child", "child-location");
        child.setValue("before");
        root.addChild(child);

        Xpp3Dom childView = root.getChild("child");
        childView.setValue("after");
        childView.setAttribute("updated", "true");
        childView.setInputLocation("updated-location");

        assertThat(root.getName()).isEqualTo("root");
        assertThat(root.getValue()).isEqualTo("initial");
        assertThat(root.getInputLocation()).isEqualTo("root-location");
        assertThat(root.getAttributeNames()).containsExactlyInAnyOrder("one", "two");
        assertThat(root.removeAttribute("one")).isTrue();
        assertThat(root.removeAttribute("missing")).isFalse();
        assertThat(root.removeAttribute(null)).isFalse();
        assertThat(root.getAttributeNames()).containsExactly("two");
        assertThat(root.getChild(0).getValue()).isEqualTo("after");
        assertThat(root.getChild(0).getAttribute("updated")).isEqualTo("true");
        assertThat(root.getChild(0).getInputLocation()).isEqualTo("updated-location");

        Xpp3Dom copyWithNewName = new Xpp3Dom(root, "renamed");
        assertThat(copyWithNewName.getName()).isEqualTo("renamed");
        assertThat(copyWithNewName.getChild("child").getValue()).isEqualTo("after");
        assertThat(copyWithNewName).isNotEqualTo(root);

        root.removeChild(root.getChild("child"));
        assertThat(root.getChildCount()).isZero();
        assertThatNullPointerException().isThrownBy(() -> root.setAttribute("invalid", null));
        assertThatNullPointerException().isThrownBy(() -> root.setAttribute(null, "invalid"));
        assertThatThrownBy(root::getParent).isInstanceOf(UnsupportedOperationException.class);
        assertThat(Xpp3Dom.isEmpty("   ")).isTrue();
        assertThat(Xpp3Dom.isNotEmpty("   ")).isTrue();
    }

    @Test
    void parsesAndMergesXpp3DomTrees() throws Exception {
        Xpp3Dom dominant = Xpp3DomBuilder.build(new StringReader("""
                <configuration><items combine.children="append"><item>local</item></items></configuration>
                """), false, parser -> "line-" + parser.getLineNumber());
        Xpp3Dom recessive = Xpp3DomBuilder.build(new ByteArrayInputStream(
                "<configuration><items><item>inherited</item><extra>value</extra></items></configuration>"
                        .getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8.name());

        Xpp3Dom appended = Xpp3Dom.mergeXpp3Dom(new Xpp3Dom(dominant), recessive);
        Xpp3Dom forcedMerge = Xpp3Dom.mergeXpp3Dom(new Xpp3Dom(dominant), recessive, Boolean.TRUE);

        assertThat(String.valueOf(dominant.getChild("items").getInputLocation())).startsWith("line-");
        assertThat(appended.getChild("items").getChildren("item")).extracting(Xpp3Dom::getValue)
                .containsExactly("inherited", "local");
        assertThat(appended.getChild("items").getChild("extra").getValue()).isEqualTo("value");
        assertThat(forcedMerge.getChild("items").getChildren("item")).extracting(Xpp3Dom::getValue)
                .containsExactly("local");
        assertThat(Xpp3Dom.mergeXpp3Dom(null, recessive)).isSameAs(recessive);
    }

    @Test
    void convertsXmlNodeTreesToPlexusConfiguration() throws Exception {
        XmlNode node = XmlNodeBuilder.build(new StringReader("""
                <component role="builder">
                  <implementation>default</implementation>
                  <requirements>
                    <requirement field-name="logger">console</requirement>
                  </requirements>
                </component>
                """));

        PlexusConfiguration configuration = XmlPlexusConfiguration.toPlexusConfiguration(node);

        assertThat(configuration.getName()).isEqualTo("component");
        assertThat(configuration.getAttribute("role")).isEqualTo("builder");
        assertThat(configuration.getChild("implementation").getValue()).isEqualTo("default");
        assertThat(configuration.getChild("requirements").getChild("requirement").getAttribute("field-name"))
                .isEqualTo("logger");
        assertThat(configuration.getChild("requirements").getChild("requirement").getValue()).isEqualTo("console");
        assertThat(configuration.toString()).contains("<component role=\"builder\">");
        assertThat(configuration.toString()).contains("<requirement field-name=\"logger\">console</requirement>");
    }

    @Test
    void rejectsMalformedXmlWithParserException() {
        assertThatThrownBy(() -> XmlNodeBuilder.build(new StringReader("<configuration><missing-end>")))
                .isInstanceOf(Exception.class);
    }

    private static XmlNodeBuilder.InputLocationBuilder fixedLocation(Object value) {
        return new XmlNodeBuilder.InputLocationBuilder() {
            @Override
            public Object toInputLocation(XmlPullParser parser) {
                return value;
            }
        };
    }
}
