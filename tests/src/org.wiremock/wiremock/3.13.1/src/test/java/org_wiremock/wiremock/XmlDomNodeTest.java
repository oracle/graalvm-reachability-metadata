/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wiremock.wiremock;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.common.xml.Xml;
import com.github.tomakehurst.wiremock.common.xml.XmlDocument;
import com.github.tomakehurst.wiremock.common.xml.XmlNode;
import org.junit.jupiter.api.Test;

public class XmlDomNodeTest {
    @Test
    void toStringRendersElementSelectedFromParsedXmlDocument() {
        XmlDocument document = Xml.parse("""
                <order>
                  <item sku="A-1">Blue widget</item>
                </order>
                """);

        XmlNode item = document.findNodes("/order/item").getFirst();

        assertThat(item.getAttributes()).containsEntry("sku", "A-1");
        assertThat(item.toString())
                .contains("<item")
                .contains("sku=\"A-1\"")
                .contains("Blue widget")
                .contains("</item>");
    }
}
