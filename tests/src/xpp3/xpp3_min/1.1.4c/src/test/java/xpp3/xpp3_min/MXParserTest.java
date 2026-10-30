/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xpp3.xpp3_min;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.xmlpull.mxp1.MXParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class MXParserTest {
    @Test
    void readsNamespacedElementsAndAttributesWithScopedPrefixes() throws Exception {
        XmlPullParser parser = new MXParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(new StringReader("""
                <catalog xmlns="urn:catalog" xmlns:p="urn:product" p:id="42" label="Books">
                    <p:item xmlns:p="urn:special">Guide</p:item>
                    <p:item/>
                </catalog>
                """));

        assertThat(parser.getEventType()).isEqualTo(XmlPullParser.START_DOCUMENT);
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, "urn:catalog", "catalog");
        assertThat(parser.getFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES)).isTrue();
        assertThat(parser.getDepth()).isEqualTo(1);
        assertThat(parser.getNamespaceCount(1)).isEqualTo(2);
        assertThat(parser.getNamespacePrefix(0)).isNull();
        assertThat(parser.getNamespaceUri(0)).isEqualTo("urn:catalog");
        assertThat(parser.getNamespacePrefix(1)).isEqualTo("p");
        assertThat(parser.getNamespaceUri(1)).isEqualTo("urn:product");
        assertThat(parser.getAttributeCount()).isEqualTo(2);
        assertThat(parser.getAttributeName(0)).isEqualTo("id");
        assertThat(parser.getAttributePrefix(0)).isEqualTo("p");
        assertThat(parser.getAttributeNamespace(0)).isEqualTo("urn:product");
        assertThat(parser.getAttributeType(0)).isEqualTo("CDATA");
        assertThat(parser.isAttributeDefault(0)).isFalse();
        assertThat(parser.getAttributeValue(0)).isEqualTo("42");
        assertThat(parser.getAttributeValue("urn:product", "id")).isEqualTo("42");
        assertThat(parser.getAttributeValue(null, "label")).isEqualTo("Books");
        assertThat(parser.getAttributeValue(null, "missing")).isNull();

        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, "urn:special", "item");
        assertThat(parser.getPrefix()).isEqualTo("p");
        assertThat(parser.getDepth()).isEqualTo(2);
        assertThat(parser.getNamespace("p")).isEqualTo("urn:special");
        assertThat(parser.nextText()).isEqualTo("Guide");
        parser.require(XmlPullParser.END_TAG, "urn:special", "item");

        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.require(XmlPullParser.START_TAG, "urn:product", "item");
        assertThat(parser.getNamespace("p")).isEqualTo("urn:product");
        assertThat(parser.isEmptyElementTag()).isTrue();
        assertThat(parser.nextText()).isEmpty();
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.END_TAG);
        parser.require(XmlPullParser.END_TAG, "urn:catalog", "catalog");
        assertThat(parser.next()).isEqualTo(XmlPullParser.END_DOCUMENT);
        assertThat(parser.getDepth()).isZero();
    }

    @Test
    void combinesTextCdataAndEntityReferences() throws Exception {
        XmlPullParser parser = new MXParser();
        parser.setInput(new StringReader("""
                <message title="A &amp; B">Hello &company;<![CDATA[ <team>]]>&amp;&#65;&#x42;\r
                done</message>
                """));
        parser.defineEntityReplacementText("company", "Acme");

        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        assertThat(parser.getAttributeValue(null, "title")).isEqualTo("A & B");
        assertThat(parser.next()).isEqualTo(XmlPullParser.TEXT);
        assertThat(parser.isWhitespace()).isFalse();
        String expected = "Hello Acme <team>&AB\ndone";
        assertThat(parser.getText()).isEqualTo(expected);
        int[] range = new int[2];
        char[] characters = parser.getTextCharacters(range);
        assertThat(new String(characters, range[0], range[1])).isEqualTo(expected);
        assertThat(parser.next()).isEqualTo(XmlPullParser.END_TAG);
        assertThat(parser.getName()).isEqualTo("message");
        assertThat(parser.next()).isEqualTo(XmlPullParser.END_DOCUMENT);
    }

    @Test
    void exposesDocumentAndContentTokens() throws Exception {
        XmlPullParser parser = new MXParser();
        parser.setInput(new StringReader("<!DOCTYPE root><?mode compact?><root><!--note--><![CDATA[a<b]]>&item;</root>"));
        parser.defineEntityReplacementText("item", "replacement");

        assertThat(parser.nextToken()).isEqualTo(XmlPullParser.DOCDECL);
        assertThat(parser.getText().trim()).isEqualTo("root");
        assertThat(parser.nextToken()).isEqualTo(XmlPullParser.PROCESSING_INSTRUCTION);
        assertThat(parser.getText()).isEqualTo("mode compact");
        assertThat(parser.nextToken()).isEqualTo(XmlPullParser.START_TAG);
        assertThat(parser.getName()).isEqualTo("root");
        assertThat(parser.nextToken()).isEqualTo(XmlPullParser.COMMENT);
        assertThat(parser.getText()).isEqualTo("note");
        assertThat(parser.nextToken()).isEqualTo(XmlPullParser.CDSECT);
        assertThat(parser.getText()).isEqualTo("a<b");
        assertThat(parser.nextToken()).isEqualTo(XmlPullParser.ENTITY_REF);
        assertThat(parser.getName()).isEqualTo("item");
        assertThat(parser.getText()).isEqualTo("replacement");
        int[] range = new int[2];
        char[] characters = parser.getTextCharacters(range);
        assertThat(new String(characters, range[0], range[1])).isEqualTo("item");
        assertThat(parser.nextToken()).isEqualTo(XmlPullParser.END_TAG);
        assertThat(parser.nextToken()).isEqualTo(XmlPullParser.END_DOCUMENT);
    }

    @Test
    void readsEncodedStreamsAndXmlDeclaration() throws Exception {
        for (Charset charset : new Charset[]{StandardCharsets.UTF_8, StandardCharsets.UTF_16}) {
            String xml = "<?xml version=\"1.0\" encoding=\"" + charset.name()
                    + "\" standalone=\"yes\"?><greeting>caf\u00e9</greeting>";
            XmlPullParser parser = new MXParser();
            try (ByteArrayInputStream input = new ByteArrayInputStream(xml.getBytes(charset))) {
                parser.setInput(input, charset.name());
                assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
                assertThat(parser.getInputEncoding()).isEqualTo(charset.name());
                assertThat(parser.getProperty("http://xmlpull.org/v1/doc/properties.html#xmldecl-version")).isEqualTo("1.0");
                assertThat(parser.getProperty("http://xmlpull.org/v1/doc/properties.html#xmldecl-standalone")).isEqualTo(Boolean.TRUE);
                assertThat(parser.nextText()).isEqualTo("caf\u00e9");
                assertThat(parser.next()).isEqualTo(XmlPullParser.END_DOCUMENT);
            }
        }
    }

    @Test
    void defaultsInputStreamsToUtf8() throws Exception {
        XmlPullParser parser = new MXParser();
        byte[] xml = "<city>M\u00fcnchen</city>".getBytes(StandardCharsets.UTF_8);
        try (ByteArrayInputStream input = new ByteArrayInputStream(xml)) {
            parser.setInput(input, null);
            assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
            assertThat(parser.nextText()).isEqualTo("M\u00fcnchen");
            assertThat(parser.next()).isEqualTo(XmlPullParser.END_DOCUMENT);
        }
    }

    @Test
    void skipsSubtreesAndReusesParserForAnotherDocument() throws Exception {
        MXParser parser = new MXParser();
        parser.setInput(new StringReader("""
                <settings>
                    <ignored><nested>unused</nested><empty/></ignored>
                    <enabled>true</enabled>
                </settings>
                """));
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        parser.skipSubTree();
        parser.require(XmlPullParser.END_TAG, null, "ignored");
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        assertThat(parser.getName()).isEqualTo("enabled");
        assertThat(parser.nextText()).isEqualTo("true");
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.END_TAG);
        assertThat(parser.next()).isEqualTo(XmlPullParser.END_DOCUMENT);

        parser.setInput(new StringReader("<fresh value=\"new\"/>"));
        assertThat(parser.getEventType()).isEqualTo(XmlPullParser.START_DOCUMENT);
        assertThat(parser.getDepth()).isZero();
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        assertThat(parser.getName()).isEqualTo("fresh");
        assertThat(parser.getAttributeCount()).isEqualTo(1);
        assertThat(parser.getAttributeValue(null, "value")).isEqualTo("new");
        assertThat(parser.nextText()).isEmpty();
        assertThat(parser.next()).isEqualTo(XmlPullParser.END_DOCUMENT);
    }

    @Test
    void readsTextAndAttributesAcrossInputBufferBoundaries() throws Exception {
        String attribute = "abcdef".repeat(3000);
        String text = "payload ".repeat(4000);
        XmlPullParser parser = new MXParser();
        parser.setInput(new StringReader("<entry label=\"" + attribute + "\">" + text + "</entry>"));

        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        assertThat(parser.getAttributeValue(null, "label")).isEqualTo(attribute);
        assertThat(parser.nextText()).isEqualTo(text);
        assertThat(parser.next()).isEqualTo(XmlPullParser.END_DOCUMENT);
    }

    @Test
    void reportsPositionForMismatchedClosingTag() throws Exception {
        XmlPullParser parser = new MXParser();
        parser.setInput(new StringReader("""
                <root>
                    <item></other>
                </root>
                """));
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);
        assertThat(parser.nextTag()).isEqualTo(XmlPullParser.START_TAG);

        XmlPullParserException failure = assertThrows(XmlPullParserException.class, parser::next);
        assertThat(failure.getMessage()).contains("item", "other");
        assertThat(failure.getLineNumber()).isEqualTo(2);
        assertThat(failure.getColumnNumber()).isPositive();
        assertThat(parser.getPositionDescription()).contains("@2:");
    }
}
