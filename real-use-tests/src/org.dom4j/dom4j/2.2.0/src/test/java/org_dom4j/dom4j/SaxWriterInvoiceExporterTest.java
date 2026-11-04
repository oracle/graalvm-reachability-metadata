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
import org.dom4j.QName;
import org.dom4j.io.SAXWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises dom4j's SAX writer subsystem to export an invoice to an application event pipeline.
public class SaxWriterInvoiceExporterTest {
    @Test
    @Timeout(60)
    public void exportsInvoiceAsNamespaceAwareSaxEvents() throws Exception {
        Namespace billing = Namespace.get("b", "urn:billing");
        Document invoice = DocumentHelper.createDocument();
        Element root = invoice.addElement(QName.get("invoice", billing));
        root.addAttribute("id", "INV-42");
        root.addElement(QName.get("customer", billing)).addText("Acme Bakery");
        root.addElement(QName.get("line", billing))
                .addAttribute("sku", "BREAD")
                .addAttribute("quantity", "2");

        InvoiceEvents events = new InvoiceEvents();
        SAXWriter writer = new SAXWriter(events);
        writer.write(invoice);

        assertEquals(List.of("b:invoice", "b:customer", "b:line"), events.startedNames);
        assertEquals(List.of("Acme Bakery"), events.textValues);
        assertEquals(List.of("b:customer", "b:line", "b:invoice"), events.endedNames);
        assertEquals("urn:billing", events.rootNamespaceUri);
        assertEquals("INV-42", events.rootId);
        assertEquals("BREAD", events.lineSku);
        assertEquals("2", events.lineQuantity);
    }

    private static final class InvoiceEvents extends DefaultHandler {
        private final List<String> startedNames = new ArrayList<>();
        private final List<String> endedNames = new ArrayList<>();
        private final List<String> textValues = new ArrayList<>();
        private String rootNamespaceUri;
        private String rootId;
        private String lineSku;
        private String lineQuantity;

        @Override
        public void startElement(
                String uri, String localName, String qName, Attributes attributes) {
            startedNames.add(qName);
            if ("b:invoice".equals(qName)) {
                rootNamespaceUri = uri;
                rootId = attributes.getValue("id");
            }
            if ("b:line".equals(qName)) {
                lineSku = attributes.getValue("sku");
                lineQuantity = attributes.getValue("quantity");
            }
        }

        @Override
        public void characters(char[] characters, int start, int length) {
            String text = new String(characters, start, length).trim();
            if (!text.isEmpty()) {
                textValues.add(text);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            endedNames.add(qName);
        }
    }
}
