/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_xml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.codehaus.plexus.util.xml.CompactXMLWriter;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.SerializerXMLWriter;
import org.codehaus.plexus.util.xml.XMLWriter;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.Xpp3DomWriter;
import org.codehaus.plexus.util.xml.XmlStreamReader;
import org.codehaus.plexus.util.xml.XmlStreamWriter;
import org.codehaus.plexus.util.xml.XmlUtil;
import org.codehaus.plexus.util.xml.XmlWriterUtil;
import org.codehaus.plexus.util.xml.pull.MXParser;
import org.codehaus.plexus.util.xml.pull.MXSerializer;
import org.codehaus.plexus.util.xml.pull.XmlPullParser;
import org.codehaus.plexus.util.xml.pull.XmlSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Plexus_xmlTest {
    private static final String POM_LIKE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project modelVersion="4.0.0">
              <name>Plexus &amp; XML</name>
              <dependencies>
                <dependency scope="test">
                  <groupId>org.codehaus.plexus</groupId>
                  <artifactId>plexus-xml</artifactId>
                </dependency>
                <dependency scope="runtime">
                  <groupId>org.example</groupId>
                  <artifactId>sample</artifactId>
                </dependency>
              </dependencies>
              <description><![CDATA[keeps <markup> as text]]></description>
            </project>
            """;

    @Test
    void buildsXpp3DomTreeFromReaderWithAttributesRepeatedChildrenAndCdata() throws Exception {
        Xpp3Dom project = Xpp3DomBuilder.build(new StringReader(POM_LIKE_XML));

        assertThat(project.getName()).isEqualTo("project");
        assertThat(project.getAttribute("modelVersion")).isEqualTo("4.0.0");
        assertThat(project.getAttributeNames()).containsExactly("modelVersion");
        assertThat(project.getChild("name").getValue()).isEqualTo("Plexus & XML");
        assertThat(project.getChild("description").getValue()).isEqualTo("keeps <markup> as text");

        Xpp3Dom dependencies = project.getChild("dependencies");
        Xpp3Dom[] dependencyNodes = dependencies.getChildren("dependency");
        assertThat(dependencyNodes).hasSize(2);
        assertThat(dependencyNodes[0].getChildCount()).isEqualTo(2);
        assertThat(dependencyNodes[0].getAttribute("scope")).isEqualTo("test");
        assertThat(dependencyNodes[0].getChild("artifactId").getValue()).isEqualTo("plexus-xml");
        assertThat(dependencyNodes[1].getAttribute("scope")).isEqualTo("runtime");
        assertThat(dependencyNodes[1].getChild("groupId").getValue()).isEqualTo("org.example");
    }

    @Test
    void writesEscapedDomXmlAndRoundTripsThroughBuilder() throws Exception {
        Xpp3Dom configuration = new Xpp3Dom("configuration");
        configuration.setAttribute("owner", "Tom & Jerry \"classic\"");
        configuration.setAttribute("enabled", "true");

        Xpp3Dom threshold = child("threshold", "5 < 10 & 20 > 15");
        threshold.setAttribute("unit", "items");
        configuration.addChild(threshold);
        configuration.addChild(child("empty", null));

        StringWriter out = new StringWriter();
        Xpp3DomWriter.write(out, configuration);
        String xml = out.toString();

        assertThat(xml).contains("owner=\"Tom &amp; Jerry &quot;classic&quot;\"");
        assertThat(xml).contains("5 &lt; 10 &amp; 20 &gt; 15");
        assertThat(xml).contains("<empty/>");

        Xpp3Dom roundTripped = Xpp3DomBuilder.build(new StringReader(xml));
        assertThat(roundTripped).isEqualTo(configuration);
        assertThat(roundTripped.getAttribute("owner")).isEqualTo("Tom & Jerry \"classic\"");
    }

    @Test
    void copiedDomIsDeepCopyAndChildRemovalKeepsOriginalIndependent() {
        Xpp3Dom original = new Xpp3Dom("server");
        original.setAttribute("id", "central");
        original.addChild(child("url", "https://repo.example.org/releases"));
        original.addChild(child("layout", "default"));

        Xpp3Dom copy = new Xpp3Dom(original);
        copy.setAttribute("id", "mirror");
        copy.getChild("url").setValue("https://mirror.example.org/releases");
        copy.removeChild(copy.getChild("layout"));

        assertThat(original.getAttribute("id")).isEqualTo("central");
        assertThat(original.getChild("url").getValue()).isEqualTo("https://repo.example.org/releases");
        assertThat(original.getChild("layout").getValue()).isEqualTo("default");
        assertThat(copy.getAttribute("id")).isEqualTo("mirror");
        assertThat(copy.getChild("url").getValue()).isEqualTo("https://mirror.example.org/releases");
        assertThat(copy.getChild("layout")).isNull();
    }

    @Test
    void mergesDominantAndRecessiveDomByNameCombineIdAndMissingAttributes() {
        Xpp3Dom dominant = new Xpp3Dom("configuration");
        dominant.setAttribute("source", "dominant");
        dominant.addChild(child("url", "https://dominant.example.org"));
        Xpp3Dom dominantParameter = child("parameter", "dominant value");
        dominantParameter.setAttribute("combine.id", "shared");
        dominantParameter.setAttribute("name", "threads");
        dominant.addChild(dominantParameter);

        Xpp3Dom recessive = new Xpp3Dom("configuration");
        recessive.setAttribute("source", "recessive");
        recessive.setAttribute("retries", "3");
        recessive.addChild(child("url", "https://recessive.example.org"));
        recessive.addChild(child("timeout", "30"));
        Xpp3Dom recessiveParameter = child("parameter", "recessive value");
        recessiveParameter.setAttribute("combine.id", "shared");
        recessiveParameter.setAttribute("description", "fallback description");
        recessive.addChild(recessiveParameter);

        Xpp3Dom merged = Xpp3Dom.mergeXpp3Dom(dominant, recessive);

        assertThat(merged).isSameAs(dominant);
        assertThat(merged.getAttribute("source")).isEqualTo("dominant");
        assertThat(merged.getAttribute("retries")).isEqualTo("3");
        assertThat(merged.getChild("url").getValue()).isEqualTo("https://dominant.example.org");
        assertThat(merged.getChild("timeout").getValue()).isEqualTo("30");
        assertThat(merged.getChildren("parameter")).hasSize(1);
        assertThat(merged.getChild("parameter").getAttribute("name")).isEqualTo("threads");
        assertThat(merged.getChild("parameter").getAttribute("description")).isEqualTo("fallback description");
    }

    @Test
    void appendCombinationKeepsDominantAndRecessiveChildrenWithSameName() {
        Xpp3Dom dominant = new Xpp3Dom("profiles");
        dominant.setAttribute(Xpp3Dom.CHILDREN_COMBINATION_MODE_ATTRIBUTE, Xpp3Dom.CHILDREN_COMBINATION_APPEND);
        dominant.addChild(child("profile", "jdk21"));
        Xpp3Dom recessive = new Xpp3Dom("profiles");
        recessive.addChild(child("profile", "native"));

        Xpp3Dom merged = Xpp3Dom.mergeXpp3Dom(dominant, recessive);

        assertThat(merged.getChildren("profile")).extracting(Xpp3Dom::getValue).containsExactly("native", "jdk21");
    }

    @Test
    void prettyAndCompactXmlWritersHandleAttributesTextMarkupAndDocumentHeader() {
        StringWriter prettyOut = new StringWriter();
        XMLWriter prettyWriter = new PrettyPrintXMLWriter(prettyOut, "UTF-8", "<!DOCTYPE note SYSTEM \"note.dtd\">");
        prettyWriter.startElement("note");
        prettyWriter.addAttribute("priority", "high & urgent");
        prettyWriter.startElement("body");
        prettyWriter.writeText("Use <escaping> & preserve text");
        prettyWriter.endElement();
        prettyWriter.writeMarkup("<!--trusted markup-->");
        prettyWriter.endElement();

        String prettyXml = prettyOut.toString();
        assertThat(prettyXml).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(prettyXml).contains("<!DOCTYPE note SYSTEM \"note.dtd\">");
        assertThat(prettyXml).contains("priority=\"high &amp; urgent\"");
        assertThat(prettyXml).contains("Use &lt;escaping&gt; &amp; preserve text");
        assertThat(prettyXml).contains("<!--trusted markup-->");

        StringWriter compactOut = new StringWriter();
        XMLWriter compactWriter = new CompactXMLWriter(compactOut);
        compactWriter.startElement("root");
        compactWriter.startElement("child");
        compactWriter.writeText("value");
        compactWriter.endElement();
        compactWriter.endElement();

        assertThat(compactOut).hasToString("<root><child>value</child></root>");
    }

    @Test
    void pullParserReportsNamespaceEventsAttributesTextAndEmptyElements() throws Exception {
        MXParser parser = new MXParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(new StringReader("""
                <root xmlns:p="urn:items" p:flag="yes">
                  <p:item sku="A-1">alpha &amp; beta</p:item>
                  <empty />
                </root>
                """));

        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, "", "root");
        assertThat(parser.getAttributeValue("urn:items", "flag")).isEqualTo("yes");
        assertThat(parser.getNamespace("p")).isEqualTo("urn:items");

        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, "urn:items", "item");
        assertThat(parser.getPrefix()).isEqualTo("p");
        assertThat(parser.getAttributeValue("", "sku")).isEqualTo("A-1");
        assertThat(parser.nextText()).isEqualTo("alpha & beta");

        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        assertThat(parser.getName()).isEqualTo("empty");
        assertThat(parser.isEmptyElementTag()).isTrue();
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.END_TAG);
        parser.require(XmlPullParser.END_TAG, "", "empty");
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.END_TAG);
        parser.require(XmlPullParser.END_TAG, "", "root");
    }

    @Test
    void serializerWritesNamespaceAwareXmlThatCanBeParsedBack() throws Exception {
        StringWriter out = new StringWriter();
        XmlSerializer serializer = new MXSerializer();
        serializer.setOutput(out);
        serializer.startDocument("UTF-8", Boolean.TRUE);
        serializer.setPrefix("p", "urn:items");
        serializer.startTag("urn:items", "items");
        serializer.attribute("", "count", "2 & more");
        serializer.startTag("urn:items", "item");
        serializer.attribute("", "sku", "A-1");
        serializer.text("alpha < beta & gamma");
        serializer.endTag("urn:items", "item");
        serializer.endTag("urn:items", "items");
        serializer.endDocument();

        String xml = out.toString();
        assertThat(xml).contains("count=\"2 &amp; more\"");
        assertThat(xml).contains("alpha &lt; beta &amp; gamma");

        MXParser parser = new MXParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(new StringReader(xml));
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, "urn:items", "items");
        assertThat(parser.getAttributeValue("", "count")).isEqualTo("2 & more");
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, "urn:items", "item");
        assertThat(parser.getAttributeValue("", "sku")).isEqualTo("A-1");
        assertThat(parser.nextText()).isEqualTo("alpha < beta & gamma");
    }

    @Test
    void serializerXmlWriterBridgesXmlWriterCallsToNamespaceAwareSerializer() throws Exception {
        StringWriter out = new StringWriter();
        XmlSerializer serializer = new MXSerializer();
        serializer.setOutput(out);
        serializer.startDocument("UTF-8", Boolean.TRUE);
        serializer.setPrefix("cfg", "urn:config");

        SerializerXMLWriter writer = new SerializerXMLWriter("urn:config", serializer);
        writer.startElement("settings");
        writer.addAttribute("id", "alpha & beta");
        writer.startElement("entry");
        writer.writeText("enabled < true & safe");
        writer.endElement();
        writer.startElement("snippet");
        writer.writeMarkup("<raw>trusted & unescaped</raw>");
        writer.endElement();
        writer.endElement();
        serializer.endDocument();

        assertThat(writer.getExceptions()).isEmpty();
        String xml = out.toString();
        assertThat(xml).contains("cfg:id=\"alpha &amp; beta\"");
        assertThat(xml).contains("enabled &lt; true &amp; safe");
        assertThat(xml).contains("<![CDATA[<raw>trusted & unescaped</raw>]]>");

        MXParser parser = new MXParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(new StringReader(xml));
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, "urn:config", "settings");
        assertThat(parser.getAttributeValue("urn:config", "id")).isEqualTo("alpha & beta");

        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, "urn:config", "entry");
        assertThat(parser.nextText()).isEqualTo("enabled < true & safe");

        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, "urn:config", "snippet");
        assertThat(parser.nextText()).isEqualTo("<raw>trusted & unescaped</raw>");
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.END_TAG);
        parser.require(XmlPullParser.END_TAG, "urn:config", "settings");
    }

    @Test
    void xmlWriterUtilWritesSafeWrappedCommentsInsideXmlDocuments() throws Exception {
        StringWriter out = new StringWriter();
        XMLWriter writer = new CompactXMLWriter(out);
        writer.startElement("document");
        XmlWriterUtil.writeComment(writer, "Generated <!--internal--> comment text spans multiple words", 1, 2, 42);
        writer.startElement("value");
        writer.writeText("content");
        writer.endElement();
        writer.endElement();

        String xml = out.toString();
        assertThat(xml).contains("<!-- Generated internal comment text");
        assertThat(xml).contains("<!-- spans multiple words");
        assertThat(xml).doesNotContain("<!--internal-->");
        assertThat(xml).contains("<value>content</value>");

        Xpp3Dom parsed = Xpp3DomBuilder.build(new StringReader(xml));
        assertThat(parsed.getName()).isEqualTo("document");
        assertThat(parsed.getChild("value").getValue()).isEqualTo("content");
    }

    @Test
    void streamReaderAndWriterDetectDeclaredXmlEncodings() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        XmlStreamWriter xmlWriter = new XmlStreamWriter(bytes);
        xmlWriter.write("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><message>caf\u00e9</message>");
        xmlWriter.close();

        assertThat(xmlWriter.getEncoding()).isEqualToIgnoringCase("ISO-8859-1");
        String decoded = new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1);
        assertThat(decoded).contains("<message>caf\u00e9</message>");

        byte[] utf8Xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><message>snowman \u2603</message>"
                .getBytes(StandardCharsets.UTF_8);
        try (XmlStreamReader xmlReader = new XmlStreamReader(new ByteArrayInputStream(utf8Xml))) {
            assertThat(xmlReader.getEncoding()).isEqualToIgnoringCase("UTF-8");
            assertThat(readFully(xmlReader)).contains("snowman \u2603");
        }
    }

    @Test
    void streamReaderHonorsExplicitContentTypeCharset() throws Exception {
        byte[] bytes = "<?xml version=\"1.0\"?><message>plain text \u03bb</message>".getBytes(StandardCharsets.UTF_16LE);

        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        try (XmlStreamReader reader = new XmlStreamReader(input, "application/xml; charset=UTF-16LE")) {
            assertThat(reader.getEncoding()).isEqualToIgnoringCase("UTF-16LE");
            assertThat(readFully(reader)).contains("plain text \u03bb");
        }
    }

    @Test
    void xmlUtilFormatsStreamsReadersAndDetectsXmlFiles(@TempDir Path tempDir) throws Exception {
        String compact = "<catalog><book id=\"1\"><title>Effective Java</title></book><book id=\"2\"/></catalog>";
        StringWriter formatted = new StringWriter();
        XmlUtil.prettyFormat(new StringReader(compact), formatted, 4, "\n");

        assertThat(formatted.toString()).contains("\n    <book id=\"1\">");
        assertThat(formatted.toString()).contains("\n        <title>Effective Java</title>");

        ByteArrayOutputStream streamFormatted = new ByteArrayOutputStream();
        XmlUtil.prettyFormat(
                new ByteArrayInputStream(compact.getBytes(StandardCharsets.UTF_8)),
                streamFormatted,
                2,
                "\n");
        assertThat(streamFormatted.toString(StandardCharsets.UTF_8)).contains("\n  <book id=\"2\"/>");

        File xmlFile = Files.writeString(tempDir.resolve("valid.xml"), compact, StandardCharsets.UTF_8).toFile();
        Path invalidPath = tempDir.resolve("invalid.txt");
        File textFile = Files.writeString(invalidPath, "<", StandardCharsets.UTF_8).toFile();
        assertThat(XmlUtil.isXml(xmlFile)).isTrue();
        assertThat(XmlUtil.isXml(textFile)).isFalse();
        assertThatThrownBy(() -> XmlUtil.isXml(tempDir.resolve("missing.xml").toFile()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not a file");
    }

    private static Xpp3Dom child(String name, String value) {
        Xpp3Dom child = new Xpp3Dom(name);
        child.setValue(value);
        return child;
    }

    private static String readFully(Reader reader) throws Exception {
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[64];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            result.append(buffer, 0, count);
        }
        return result.toString();
    }
}
