/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_dom4j.dom4j;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.io.DOMReader;
import org.dom4j.io.DOMWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises dom4j's DOM bridge subsystem to enrich an invoice in an application DOM pipeline.
public class DomBridgeInvoiceEnricherTest {
    @Test
    @Timeout(60)
    public void enrichesInvoiceAndReturnsNamespaceAwareDom() throws Exception {
        org.w3c.dom.Document incoming = parse("""
                <invoice xmlns=\"urn:billing\" id=\"INV-42\">
                  <customer>Acme Bakery</customer>
                  <line sku=\"BREAD\" quantity=\"2\"/>
                </invoice>
                """);

        Document invoice = new DOMReader().read(incoming);
        Namespace billing = Namespace.get("b", "urn:billing");
        Element root = invoice.getRootElement();
        root.addElement(QName.get("payment", billing))
                .addAttribute("method", "card")
                .addText("captured");

        org.w3c.dom.Document returned = new DOMWriter().write(invoice);
        org.w3c.dom.Element returnedRoot = returned.getDocumentElement();
        NodeList payments = returned.getElementsByTagNameNS("urn:billing", "payment");

        assertNotNull(returnedRoot);
        assertEquals("invoice", returnedRoot.getLocalName());
        assertEquals("INV-42", returnedRoot.getAttributeNS(null, "id"));
        assertEquals(1, payments.getLength());
        assertEquals("card", ((org.w3c.dom.Element) payments.item(0)).getAttributeNS(null, "method"));
        assertEquals("captured", payments.item(0).getTextContent());
    }

    private static org.w3c.dom.Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
