/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_xml_jaxp_util;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.util.xml.pretty.XmlPrettyPrinter;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Camel_xml_jaxp_utilTest {
    @Test
    void prettyPrintFormatsNestedElementsAttributesTextAndEmptyElements() throws Exception {
        String xml = """
                <root><child id="42">value</child><empty/></root>
                """.trim();

        String prettyXml = XmlPrettyPrinter.pettyPrint(xml, 2);

        assertThat(prettyXml).isEqualTo("""
                <root>
                  <child id="42">
                    value
                  </child>
                  <empty>
                  </empty>
                </root>""");
    }

    @Test
    void prettyPrintNormalizesSingleQuotedAttributes() throws Exception {
        String xml = """
                <root><item id='123' category='books'>Camel in Action</item></root>
                """.trim();

        String prettyXml = XmlPrettyPrinter.pettyPrint(xml, 2);

        assertThat(prettyXml)
                .contains("<item ")
                .contains("id=\"123\"")
                .contains("category=\"books\"")
                .contains("Camel in Action")
                .doesNotContain("id='123'")
                .doesNotContain("category='books'");
    }

    @Test
    void prettyPrintUsesRequestedIndentationWidth() throws Exception {
        String xml = """
                <root><group><entry>camel</entry></group></root>
                """.trim();

        String prettyXml = XmlPrettyPrinter.pettyPrint(xml, 3);

        assertThat(prettyXml).isEqualTo("""
                <root>
                   <group>
                      <entry>
                         camel
                      </entry>
                   </group>
                </root>""");
    }

    @Test
    void prettyPrintNormalizesExistingWhitespaceBetweenSiblingElements() throws Exception {
        String xml = """
                <menu>
                    <item>Fosters</item>
                    <item>Bell Expedition</item>
                </menu>
                """.trim();

        String prettyXml = XmlPrettyPrinter.pettyPrint(xml, 2);

        assertThat(prettyXml).isEqualTo("""
                <menu>
                  <item>
                    Fosters
                  </item>
                  <item>
                    Bell Expedition
                  </item>
                </menu>""");
    }

    @Test
    void prettyPrintPreservesQualifiedNamesWhenNamespacesAreDisabled() throws Exception {
        String xml = """
                <ns:root xmlns:ns="urn:test"><ns:child name="camel"/></ns:root>
                """.trim();

        String prettyXml = XmlPrettyPrinter.pettyPrint(xml, 4, true);

        assertThat(prettyXml).isEqualTo("""
                <ns:root xmlns:ns="urn:test">
                    <ns:child name="camel">
                    </ns:child>
                </ns:root>""");
    }

    @Test
    void prettyPrintFormatsDocumentWhenXmlDeclarationIsRequested() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?><catalog><book>Apache Camel</book></catalog>
                """.trim();

        String prettyXml = XmlPrettyPrinter.pettyPrint(xml, 2, true);

        assertThat(prettyXml).isIn("""
                <?xml version="1.0" encoding="UTF-8"?>
                <catalog>
                  <book>
                    Apache Camel
                  </book>
                </catalog>""", """
                <catalog>
                  <book>
                    Apache Camel
                  </book>
                </catalog>""");
    }

    @Test
    void prettyPrintOmitsXmlDeclarationUnlessRequested() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?><catalog><book>Apache Camel</book></catalog>
                """.trim();

        String prettyXml = XmlPrettyPrinter.pettyPrint(xml, 2);

        assertThat(prettyXml).isEqualTo("""
                <catalog>
                  <book>
                    Apache Camel
                  </book>
                </catalog>""");
    }

    @Test
    void colorPrintAppliesCallbackToElementsAttributesAndText() throws Exception {
        String xml = """
                <root><child name="first">text</child></root>
                """.trim();

        String colorizedXml = XmlPrettyPrinter.colorPrint(
                xml,
                1,
                false,
                (kind, value) -> "[" + kind + ":" + value + "]");

        assertThat(colorizedXml).isEqualTo("""
                [2:<root>]
                 [2:<child] [3:name][5:=][6:\"][4:first][6:\"][2:>]
                  [7:text]
                 [2:</child>]
                [2:</root>]""");
    }

    @Test
    void colorPrintFormatsDocumentWhenXmlDeclarationIsRequested() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?><root/>
                """.trim();

        String colorizedXml = XmlPrettyPrinter.colorPrint(
                xml,
                2,
                true,
                (kind, value) -> "[" + kind + ":" + value + "]");

        assertThat(colorizedXml).isIn("""
                [1:<?xml version="1.0" encoding="UTF-8"?>]
                [2:<root>]
                [2:</root>]""", """
                [2:<root>]
                [2:</root>]""");
    }

    @Test
    void colorPrintDoesNotExposeDeclarationWhenNotRequested() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?><root><child>value</child></root>
                """.trim();
        List<Integer> colorKinds = new ArrayList<>();

        String colorizedXml = XmlPrettyPrinter.colorPrint(xml, 2, false, (kind, value) -> {
            colorKinds.add(kind);
            return value;
        });

        assertThat(colorizedXml).isEqualTo("""
                <root>
                  <child>
                    value
                  </child>
                </root>""");
        assertThat(colorKinds).doesNotContain(XmlPrettyPrinter.ColorPrintElement.DECLARATION);
    }

    @Test
    void colorPrintReportsCallbackEventsInDocumentOrder() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?><root id="r1"><child>body</child></root>
                """.trim();
        List<String> events = new ArrayList<>();

        XmlPrettyPrinter.colorPrint(xml, 2, true, (kind, value) -> {
            events.add(kind + ":" + value);
            return value;
        });

        List<String> expectedContentEvents = List.of(
                XmlPrettyPrinter.ColorPrintElement.ELEMENT + ":<root",
                XmlPrettyPrinter.ColorPrintElement.ATTRIBUTE_KEY + ":id",
                XmlPrettyPrinter.ColorPrintElement.ATTRIBUTE_VALUE + ":r1",
                XmlPrettyPrinter.ColorPrintElement.ATTRIBUTE_EQUAL + ":=",
                XmlPrettyPrinter.ColorPrintElement.ATTRIBUTE_QUOTE + ":\"",
                XmlPrettyPrinter.ColorPrintElement.ELEMENT + ":>",
                XmlPrettyPrinter.ColorPrintElement.ELEMENT + ":<child>",
                XmlPrettyPrinter.ColorPrintElement.VALUE + ":body",
                XmlPrettyPrinter.ColorPrintElement.ELEMENT + ":</child>",
                XmlPrettyPrinter.ColorPrintElement.ELEMENT + ":</root>");
        if (events.get(0).startsWith(XmlPrettyPrinter.ColorPrintElement.DECLARATION + ":")) {
            assertThat(events)
                    .startsWith(XmlPrettyPrinter.ColorPrintElement.DECLARATION
                            + ":<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                    .endsWith(expectedContentEvents.toArray(String[]::new));
        } else {
            assertThat(events).containsExactlyElementsOf(expectedContentEvents);
        }
    }

    @Test
    void colorPrintExposesStableColorElementConstantsToCallbacks() throws Exception {
        String xml = """
                <root><child key="value">body</child></root>
                """.trim();

        String colorizedXml = XmlPrettyPrinter.colorPrint(xml, 1, false, (kind, value) -> switch (kind) {
            case XmlPrettyPrinter.ColorPrintElement.ELEMENT -> "ELEMENT(" + value + ")";
            case XmlPrettyPrinter.ColorPrintElement.ATTRIBUTE_KEY -> "KEY(" + value + ")";
            case XmlPrettyPrinter.ColorPrintElement.ATTRIBUTE_VALUE -> "VALUE(" + value + ")";
            case XmlPrettyPrinter.ColorPrintElement.ATTRIBUTE_EQUAL -> "EQUAL";
            case XmlPrettyPrinter.ColorPrintElement.ATTRIBUTE_QUOTE -> "QUOTE";
            case XmlPrettyPrinter.ColorPrintElement.VALUE -> "TEXT(" + value + ")";
            default -> "OTHER(" + value + ")";
        });

        assertThat(colorizedXml)
                .contains("ELEMENT(<root>)")
                .contains("ELEMENT(<child)")
                .contains("KEY(key)EQUALQUOTEVALUE(value)QUOTE")
                .contains("TEXT(body)")
                .contains("ELEMENT(</child>)")
                .contains("ELEMENT(</root>)");
    }

    @Test
    void prettyPrintRejectsDocumentsWithDoctypeDeclarations() {
        String xml = """
                <!DOCTYPE root [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><root>&xxe;</root>
                """.trim();

        assertThatThrownBy(() -> XmlPrettyPrinter.pettyPrint(xml, 2))
                .isInstanceOf(SAXParseException.class)
                .hasMessageContaining("DOCTYPE");
    }

    @Test
    void prettyPrintRejectsMalformedXml() {
        assertThatThrownBy(() -> XmlPrettyPrinter.pettyPrint("<root><child></root>", 2))
                .isInstanceOf(SAXParseException.class);
    }
}
