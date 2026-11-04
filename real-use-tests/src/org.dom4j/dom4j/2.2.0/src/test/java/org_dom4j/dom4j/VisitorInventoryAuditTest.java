/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_dom4j.dom4j;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.VisitorSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises dom4j's visitor subsystem to audit inventory items in an application catalog.
public class VisitorInventoryAuditTest {
    @Test
    @Timeout(60)
    public void auditsCatalogItemsWithVisitor() throws Exception {
        String catalogXml = """
                <catalog warehouse="lyon">
                  <item sku="TEA-17" category="tea" quantity="12">
                    <name>Green tea</name>
                  </item>
                  <item sku="COF-04" category="coffee" quantity="8">
                    <name>Espresso beans</name>
                  </item>
                  <item sku="MUG-09" category="drinkware" quantity="0">
                    <name>Travel mug</name>
                  </item>
                </catalog>
                """;

        Document catalog = DocumentHelper.parseText(catalogXml);
        InventoryVisitor visitor = new InventoryVisitor();
        catalog.accept(visitor);

        assertEquals(List.of(
                new InventoryItem("TEA-17", "tea", "Green tea", 12),
                new InventoryItem("COF-04", "coffee", "Espresso beans", 8),
                new InventoryItem("MUG-09", "drinkware", "Travel mug", 0)), visitor.items());
        assertEquals(20, visitor.totalUnits());
        assertEquals(1, visitor.outOfStockItems());
    }

    private static final class InventoryVisitor extends VisitorSupport {
        private final List<InventoryItem> items = new ArrayList<>();

        @Override
        public void visit(Element element) {
            if ("item".equals(element.getName())) {
                items.add(new InventoryItem(
                        element.attributeValue("sku"),
                        element.attributeValue("category"),
                        element.elementTextTrim("name"),
                        Integer.parseInt(element.attributeValue("quantity"))));
            }
        }

        private List<InventoryItem> items() {
            return List.copyOf(items);
        }

        private int totalUnits() {
            return items.stream().mapToInt(InventoryItem::quantity).sum();
        }

        private long outOfStockItems() {
            return items.stream().filter(item -> item.quantity() == 0).count();
        }
    }

    private record InventoryItem(String sku, String category, String name, int quantity) {
    }
}
