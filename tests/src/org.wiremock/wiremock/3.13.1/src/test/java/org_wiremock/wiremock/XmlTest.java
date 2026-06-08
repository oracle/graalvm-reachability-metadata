/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wiremock.wiremock;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.common.xml.Xml;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

public class XmlTest {
    @Test
    void prettyPrintFormatsParsedXmlDocument() {
        Xml.optimizeFactoriesLoading();
        Document document = Xml.read("<order><item sku=\"A-1\">Blue widget</item></order>");

        String formattedXml = Xml.prettyPrint(document);

        assertThat(formattedXml)
                .contains("<order>")
                .contains("<item sku=\"A-1\">Blue widget</item>")
                .contains("</order>");
    }
}
