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
import org.dom4j.Namespace;
import org.dom4j.Node;
import org.dom4j.QName;
import org.dom4j.XPath;
import org.jaxen.VariableContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises dom4j's XPath subsystem to select namespaced catalog entries for an application report.
public class XPathCatalogQueryTest {
    @Test
    @Timeout(60)
    public void selectsAvailableProductsByNamespaceAndCategory() throws Exception {
        String catalogXml = """
                <catalog xmlns="urn:shop:catalog" xmlns:inv="urn:shop:inventory">
                  <product sku="A-17" category="tea" status="available">
                    <name>Green tea</name>
                    <inv:stock>12</inv:stock>
                  </product>
                  <product sku="B-04" category="coffee" status="available">
                    <name>Espresso beans</name>
                    <inv:stock>8</inv:stock>
                  </product>
                  <product sku="C-22" category="tea" status="backorder">
                    <name>Oolong tea</name>
                    <inv:stock>0</inv:stock>
                  </product>
                </catalog>
                """;

        Document catalog = DocumentHelper.parseText(catalogXml);
        XPath availableTea = DocumentHelper.createXPath(
                "/c:catalog/c:product[@category=$category and @status='available']",
                new CategoryVariables("tea"));
        availableTea.setNamespaceURIs(Map.of("c", "urn:shop:catalog"));

        List<Node> matches = availableTea.selectNodes(catalog);

        assertEquals(1, matches.size());
        Element product = (Element) matches.getFirst();
        assertEquals("A-17", product.attributeValue("sku"));
        assertEquals("Green tea", product.elementTextTrim("name"));
        Namespace inventory = Namespace.get("inv", "urn:shop:inventory");
        QName stock = QName.get("stock", inventory);
        assertNotNull(product.element(stock));
        assertEquals("12", product.elementTextTrim(stock));
    }

    private static final class CategoryVariables implements VariableContext {
        private final String category;

        private CategoryVariables(String category) {
            this.category = category;
        }

        @Override
        public Object getVariableValue(String namespaceUri, String prefix, String localName) {
            if ("category".equals(localName)) {
                return category;
            }
            throw new IllegalArgumentException("Unknown XPath variable: " + localName);
        }
    }
}
