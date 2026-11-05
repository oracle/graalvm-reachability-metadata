/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xmlunit.xmlunit_legacy;

import org.custommonkey.xmlunit.XMLUnit;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import static org.assertj.core.api.Assertions.assertThat;

public class Xmlunit_legacyTest {
    @Test
    void parsesControlAndTestDocumentsWithNamespacesAndEscapedText() throws Exception {
        String xml = """
                <order xmlns="urn:orders" id="purchase">
                    <item>Pen &amp; paper</item>
                </order>
                """;

        Document control = XMLUnit.buildControlDocument(xml);
        Document test = XMLUnit.buildTestDocument(xml);

        for (Document document : new Document[]{control, test}) {
            assertThat(document.getDocumentElement().getLocalName()).isEqualTo("order");
            assertThat(document.getDocumentElement().getNamespaceURI()).isEqualTo("urn:orders");
            assertThat(document.getDocumentElement().getAttribute("id")).isEqualTo("purchase");
            assertThat(document.getElementsByTagNameNS("urn:orders", "item").item(0).getTextContent())
                    .isEqualTo("Pen & paper");
        }
    }
}
