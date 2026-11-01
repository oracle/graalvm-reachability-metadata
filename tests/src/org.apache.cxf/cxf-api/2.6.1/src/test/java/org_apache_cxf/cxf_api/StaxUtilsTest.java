/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_api;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamSource;

import org.apache.cxf.staxutils.StaxUtils;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StaxUtilsTest {
    @Test
    void copiesAJdkStaxSourcePreservingNamespacesAndEscapedContent() throws Exception {
        String xml = """
                <o:order xmlns:o="urn:orders" id="42"><o:item>Tea &amp; cake</o:item></o:order>
                """;
        XMLStreamReader reader = StaxUtils.createXMLStreamReader(new StringReader(xml));
        StringWriter output = new StringWriter();
        XMLStreamWriter writer = StaxUtils.createXMLStreamWriter(output);
        try {
            StaxUtils.copy(new StAXSource(reader), writer);
            writer.flush();
        } finally {
            writer.close();
            reader.close();
        }

        Document restored = StaxUtils.read(new StreamSource(new StringReader(output.toString())));
        Element root = restored.getDocumentElement();
        assertEquals("urn:orders", root.getNamespaceURI());
        assertEquals("order", root.getLocalName());
        assertEquals("42", root.getAttribute("id"));
        assertEquals("Tea & cake", root.getTextContent());
        assertEquals(1, root.getElementsByTagNameNS("urn:orders", "item").getLength());
    }

    @Test
    void resolvesQNameTextUsingTheReadersNamespaceContext() throws Exception {
        String xml = "<kind xmlns:p=\"urn:products\">p:Tea</kind>";
        XMLStreamReader reader = StaxUtils.createXMLStreamReader(new StringReader(xml));
        try {
            assertTrue(StaxUtils.skipToStartOfElement(reader));
            assertEquals(new QName("urn:products", "Tea", "p"), StaxUtils.readQName(reader));
        } finally {
            reader.close();
        }
    }
}
