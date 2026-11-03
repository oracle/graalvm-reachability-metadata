/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xmlunit.xmlunit_core;

import java.util.Map;

import javax.xml.namespace.QName;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xmlunit.builder.Input;
import org.xmlunit.util.Convert;
import org.xmlunit.util.Nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises XMLUnit's DOM utility subsystem to extract a namespaced catalog item for a product page.
public class CatalogDomExtractionTest {
    private static final String CATALOG_NAMESPACE = "urn:acme:catalog";
    private static final String INVENTORY_NAMESPACE = "urn:acme:inventory";

    @Test
    @Timeout(60)
    public void extractsProductDetailsFromSupplierCatalog() {
        Document catalog = Convert.toDocument(Input.fromString("""
                <catalog xmlns="urn:acme:catalog" xmlns:inventory="urn:acme:inventory">
                    <product inventory:sku="LAMP-42" category="lighting">
                        <name>Desk <style>lamp</style></name>
                        <description>
                            A warm\n                            light for a reading nook.
                        </description>
                    </product>
                </catalog>
                """).build());
        Node product = catalog.getElementsByTagNameNS(CATALOG_NAMESPACE, "product").item(0);

        assertEquals(new QName(CATALOG_NAMESPACE, "product"), Nodes.getQName(product));
        assertEquals(Map.of(
                new QName(INVENTORY_NAMESPACE, "sku"), "LAMP-42",
                new QName("category"), "lighting"), Nodes.getAttributes(product));

        Node name = catalog.getElementsByTagNameNS(CATALOG_NAMESPACE, "name").item(0);
        assertEquals("Desk ", Nodes.getMergedNestedText(name));

        Node description = catalog.getElementsByTagNameNS(CATALOG_NAMESPACE, "description").item(0);
        Node normalizedDescription = Nodes.normalizeWhitespace(description);
        assertEquals("A warm light for a reading nook.", normalizedDescription.getTextContent());
    }
}
