/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_x_stream.mxparser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import io.github.xstream.mxparser.MXParser;
import org.junit.jupiter.api.Test;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

public class MxparserTest {
    private static final String PROPERTY_LOCATION = "http://xmlpull.org/v1/doc/properties.html#location";
    private static final String PROPERTY_XMLDECL_VERSION = "http://xmlpull.org/v1/doc/properties.html#xmldecl-version";
    private static final String PROPERTY_XMLDECL_STANDALONE = "http://xmlpull.org/v1/doc/properties.html#xmldecl-standalone";
    private static final String PROPERTY_XMLDECL_CONTENT = "http://xmlpull.org/v1/doc/properties.html#xmldecl-content";

    @Test
    void factoryDiscoveryCreatesNamespaceAwareParser() throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);

        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new StringReader("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <root xmlns="urn:default" xmlns:ns="urn:extra" ns:flag="on"><ns:item id="7">value</ns:item><empty attr="x"/></root>
                """));

        assertThat(parser).isInstanceOf(MXParser.class);
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        assertThat(parser.getProperty(PROPERTY_XMLDECL_VERSION)).isEqualTo("1.0");
        assertThat(parser.getProperty(PROPERTY_XMLDECL_STANDALONE)).isEqualTo(Boolean.TRUE);
        assertThat(parser.getProperty(PROPERTY_XMLDECL_CONTENT).toString()).contains("standalone=\"yes\"");

        parser.require(XmlPullParser.START_TAG, "urn:default", "root");
        assertThat(parser.getNamespace()).isEqualTo("urn:default");
        assertThat(parser.getNamespace(null)).isEqualTo("urn:default");
        assertThat(parser.getNamespace("ns")).isEqualTo("urn:extra");
        assertThat(parser.getNamespaceCount(parser.getDepth())).isEqualTo(2);
        assertThat(parser.getAttributeCount()).isEqualTo(1);
        assertThat(parser.getAttributePrefix(0)).isEqualTo("ns");
        assertThat(parser.getAttributeNamespace(0)).isEqualTo("urn:extra");
        assertThat(parser.getAttributeName(0)).isEqualTo("flag");
        assertThat(parser.getAttributeValue(0)).isEqualTo("on");
        assertThat(parser.getAttributeValue("urn:extra", "flag")).isEqualTo("on");

        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, "urn:extra", "item");
        assertThat(parser.getAttributeValue(null, "id")).isEqualTo("7");
        assertThat(parser.nextText()).isEqualTo("value");
        parser.require(XmlPullParser.END_TAG, "urn:extra", "item");

        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, "urn:default", "empty");
        assertThat(parser.isEmptyElementTag()).isTrue();
        assertThat(parser.getAttributeValue(null, "attr")).isEqualTo("x");

        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.END_TAG);
        parser.require(XmlPullParser.END_TAG, "urn:default", "empty");
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.END_TAG);
        parser.require(XmlPullParser.END_TAG, "urn:default", "root");
        assertThat(parser.next()).isEqualTo(XmlPullParser.END_DOCUMENT);
    }

    @Test
    void parserMergesCdataAndEntityTextFromInputStream() throws Exception {
        MXParser parser = new MXParser();

        byte[] xml = "<?xml version=\"1.0\"?><root>alpha<![CDATA[<beta>]]>&rocket;<?pi ignore?><child/>tail</root>"
                .getBytes(StandardCharsets.UTF_8);
        parser.setInput(new ByteArrayInputStream(xml), StandardCharsets.UTF_8.name());
        parser.defineEntityReplacementText("rocket", "<gamma>");
        parser.setProperty(PROPERTY_LOCATION, "sample.xml");

        assertThat(parser.getInputEncoding()).isEqualTo("UTF-8");
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        assertThat(parser.getProperty(PROPERTY_XMLDECL_VERSION)).isEqualTo("1.0");

        assertThat(parser.next()).isEqualTo(XmlPullParser.TEXT);
        assertThat(parser.getText()).isEqualTo("alpha<beta><gamma>");
        assertThat(parser.isWhitespace()).isFalse();
        int[] textRange = new int[2];
        char[] textBuffer = parser.getTextCharacters(textRange);
        assertThat(new String(textBuffer, textRange[0], textRange[1])).isEqualTo("alpha<beta><gamma>");
        assertThat(parser.getPositionDescription()).contains("sample.xml");

        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, null, "child");
        assertThat(parser.isEmptyElementTag()).isTrue();
        assertThat(parser.next()).isEqualTo(XmlPullParser.END_TAG);
        parser.require(XmlPullParser.END_TAG, null, "child");

        assertThat(parser.next()).isEqualTo(XmlPullParser.TEXT);
        assertThat(parser.getText()).isEqualTo("tail");
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.END_TAG);
        parser.require(XmlPullParser.END_TAG, null, "root");
        assertThat(parser.next()).isEqualTo(XmlPullParser.END_DOCUMENT);
    }

    @Test
    void nextTokenExposesDocdeclCommentProcessingInstructionAndEntityReference() throws Exception {
        MXParser parser = new MXParser();
        parser.setInput(new StringReader(
                "<?xml version='1.0'?><!DOCTYPE root SYSTEM 'noop.dtd'><root><!--note--><?work done?><item>&custom;<![CDATA[ tail ]]></item></root>"));
        parser.defineEntityReplacementText("custom", "entity");

        assertThat(parser.nextToken()).isEqualTo(XmlPullParser.START_DOCUMENT);
        assertThat(parser.getProperty(PROPERTY_XMLDECL_VERSION)).isEqualTo("1.0");

        assertThat(parser.nextToken()).isEqualTo(XmlPullParser.DOCDECL);
        assertThat(parser.getText()).contains("root SYSTEM 'noop.dtd'");

        assertThat(parser.nextToken()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, null, "root");

        assertThat(parser.nextToken()).isEqualTo(XmlPullParser.COMMENT);
        assertThat(parser.getText()).isEqualTo("note");

        assertThat(parser.nextToken()).isEqualTo(XmlPullParser.PROCESSING_INSTRUCTION);
        assertThat(parser.getText()).contains("work done");

        assertThat(parser.nextToken()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, null, "item");

        assertThat(parser.nextToken()).isEqualTo(XmlPullParser.ENTITY_REF);
        assertThat(parser.getName()).isEqualTo("custom");
        assertThat(parser.getText()).isEqualTo("entity");

        assertThat(parser.nextToken()).isEqualTo(XmlPullParser.CDSECT);
        assertThat(parser.getText()).isEqualTo(" tail ");
        assertThat(parser.nextToken()).isEqualTo(XmlPullParser.END_TAG);
        parser.require(XmlPullParser.END_TAG, null, "item");
        assertThat(parser.nextToken()).isEqualTo(XmlPullParser.END_TAG);
        parser.require(XmlPullParser.END_TAG, null, "root");
        assertThat(parser.nextToken()).isEqualTo(XmlPullParser.END_DOCUMENT);
    }

    @Test
    void skipSubTreeAdvancesToMatchingEndTag() throws Exception {
        MXParser parser = new MXParser();
        parser.setInput(new StringReader("<root><skip><nested><leaf/></nested></skip><keep>done</keep></root>"));

        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, null, "root");
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, null, "skip");

        parser.skipSubTree();
        parser.require(XmlPullParser.END_TAG, null, "skip");

        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, null, "keep");
        assertThat(parser.nextText()).isEqualTo("done");
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.END_TAG);
        parser.require(XmlPullParser.END_TAG, null, "root");
        assertThat(parser.next()).isEqualTo(XmlPullParser.END_DOCUMENT);
    }
}
