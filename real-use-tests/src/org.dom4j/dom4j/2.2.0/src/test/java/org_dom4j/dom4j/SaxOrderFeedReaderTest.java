/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_dom4j.dom4j;

import org.dom4j.Element;
import org.dom4j.ElementHandler;
import org.dom4j.ElementPath;
import org.dom4j.io.SAXReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises dom4j's SAX reader subsystem to extract order summaries from a feed.
public class SaxOrderFeedReaderTest {
    @Test
    @Timeout(60)
    public void extractsOrdersWhileReadingFeed() throws Exception {
        String feed = """
                <orders>
                  <order id="ORD-17">
                    <customer>Acme Bakery</customer>
                    <total currency="EUR">42.50</total>
                  </order>
                  <order id="ORD-18">
                    <customer>Northwind Cafe</customer>
                    <total currency="USD">18.00</total>
                  </order>
                </orders>
                """;
        List<OrderSummary> summaries = new ArrayList<>();
        SAXReader reader = SAXReader.createDefault();
        reader.setStripWhitespaceText(true);
        reader.addHandler("/orders/order", new ElementHandler() {
            @Override
            public void onStart(ElementPath path) {
                // The application only needs the completed order.
            }

            @Override
            public void onEnd(ElementPath path) {
                Element order = path.getCurrent();
                Element total = order.element("total");
                summaries.add(new OrderSummary(
                        order.attributeValue("id"),
                        order.elementTextTrim("customer"),
                        total.attributeValue("currency"),
                        total.getTextTrim()));
                order.detach();
            }
        });

        reader.read(new StringReader(feed));

        assertEquals(List.of(
                new OrderSummary("ORD-17", "Acme Bakery", "EUR", "42.50"),
                new OrderSummary("ORD-18", "Northwind Cafe", "USD", "18.00")), summaries);
    }

    private record OrderSummary(String id, String customer, String currency, String total) {
    }
}
