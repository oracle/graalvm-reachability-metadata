/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_api;

import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.transform.stream.StreamSource;

import org.apache.cxf.helpers.XMLUtils;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class XMLUtilsTest {
    @Test
    void parsesAndSerializesNamespacedDocuments() throws Exception {
        String xml = """
                <order xmlns="urn:orders" id="o17"><item>Tea &amp; biscuits</item></order>
                """;
        Document document = XMLUtils.parse(new InputSource(new StringReader(xml)));
        Element root = document.getDocumentElement();
        assertEquals("urn:orders", root.getNamespaceURI());
        assertEquals("o17", root.getAttribute("id"));

        StringWriter output = new StringWriter();
        XMLUtils.writeTo(document, output);
        Document restored = XMLUtils.parse(output.toString());
        assertEquals("urn:orders", restored.getDocumentElement().getNamespaceURI());
        assertEquals("Tea & biscuits", restored.getDocumentElement().getTextContent());
        assertEquals("o17", restored.getDocumentElement().getAttribute("id"));
    }

    @Test
    void exposesDocumentAsAnXmlInputStream() throws Exception {
        Document document = XMLUtils.parse("<receipt currency=\"EUR\"><total>12.50</total></receipt>");
        try (InputStream input = XMLUtils.getInputStream(document)) {
            Document restored = XMLUtils.parse(input);
            assertEquals("EUR", restored.getDocumentElement().getAttribute("currency"));
            assertEquals("12.50", restored.getDocumentElement().getTextContent());
        }
    }

    @Test
    void transformsAStreamSourceIntoADocument() throws Exception {
        StreamSource source = new StreamSource(new StringReader("<status code=\"200\">accepted</status>"));
        Node result = XMLUtils.fromSource(source);
        Document document = (Document) result;
        assertEquals("status", document.getDocumentElement().getNodeName());
        assertEquals("200", document.getDocumentElement().getAttribute("code"));
        assertEquals("accepted", document.getDocumentElement().getTextContent());
    }
}
