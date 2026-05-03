/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_xml_jaxp_util;

import org.apache.camel.util.xml.pretty.XmlPrettyPrinter;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CamelXmlJaxpUtilTest {
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
    void prettyPrintIncludesXmlDeclarationWhenRequested() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?><catalog><book>Apache Camel</book></catalog>
                """.trim();

        String prettyXml = XmlPrettyPrinter.pettyPrint(xml, 2, true);

        assertThat(prettyXml).isEqualTo("""
                <?xml version="1.0" encoding="UTF-8"?>
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
    void colorPrintAppliesCallbackToXmlDeclarationWhenRequested() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?><root/>
                """.trim();

        String colorizedXml = XmlPrettyPrinter.colorPrint(
                xml,
                2,
                true,
                (kind, value) -> "[" + kind + ":" + value + "]");

        assertThat(colorizedXml).isEqualTo("""
                [1:<?xml version="1.0" encoding="UTF-8"?>]
                [2:<root>]
                [2:</root>]""");
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
