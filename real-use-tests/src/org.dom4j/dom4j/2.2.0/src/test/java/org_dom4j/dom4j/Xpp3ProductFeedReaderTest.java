/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_dom4j.dom4j;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.XPP3Reader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises dom4j's XPP3 pull-parser subsystem to import a product feed for an application catalog.
public class Xpp3ProductFeedReaderTest {
    @Test
    @Timeout(60)
    public void importsAvailableProductsFromFeed() throws Exception {
        String feed = """
                <products generated="2025-01-15">
                  <product sku="TEA-17" available="true">
                    <name>Green tea</name>
                    <price currency="EUR">8.40</price>
                    <tags><tag>organic</tag><tag>loose-leaf</tag></tags>
                  </product>
                  <product sku="COF-04" available="false">
                    <name>Espresso beans</name>
                    <price currency="EUR">12.00</price>
                    <tags><tag>whole-bean</tag></tags>
                  </product>
                </products>
                """;
        Document document = new XPP3Reader().read(new StringReader(feed));
        Element product = document.getRootElement().element("product");
        Element price = product.element("price");
        List<String> tags = product.element("tags").elements("tag").stream()
                .map(Element::getTextTrim)
                .toList();

        assertEquals("2025-01-15", document.getRootElement().attributeValue("generated"));
        assertEquals(new ProductSummary(
                "TEA-17", "Green tea", "EUR", "8.40", List.of("organic", "loose-leaf")),
                new ProductSummary(
                        product.attributeValue("sku"),
                        product.elementTextTrim("name"),
                        price.attributeValue("currency"),
                        price.getTextTrim(),
                        tags));
        assertEquals("false", document.getRootElement().elements("product").get(1)
                .attributeValue("available"));
    }

    private record ProductSummary(
            String sku, String name, String currency, String price, List<String> tags) {
    }
}
