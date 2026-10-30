/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ws_xmlschema.xmlschema_core;

import org.apache.ws.commons.schema.XmlSchema;
import org.apache.ws.commons.schema.XmlSchemaCollection;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.apache.ws.commons.schema.constants.Constants;
import org.apache.xerces.jaxp.DocumentBuilderFactoryImpl;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.namespace.QName;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DOMUtilTest {
    @Test
    void preservesInputEncodingWhenReadingDocument() throws Exception {
        Document document = parseSchema();
        XmlSchemaCollection collection = new XmlSchemaCollection();

        XmlSchema schema = collection.read(document, "urn:encoding:schema");

        assertSchema(schema);
    }

    @Test
    void preservesDeclaredXmlEncodingWhenReadingElement() throws Exception {
        Document document = parseSchema();
        XmlSchemaCollection collection = new XmlSchemaCollection();

        XmlSchema schema = collection.read(document.getDocumentElement());

        assertSchema(schema);
    }

    private static Document parseSchema() throws Exception {
        String schema = """
                <?xml version="1.0" encoding="ISO-8859-1"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="urn:encoding">
                    <xs:element name="greeting" type="xs:string"/>
                </xs:schema>
                """;
        DocumentBuilderFactoryImpl factory = new DocumentBuilderFactoryImpl();
        factory.setNamespaceAware(true);
        try (ByteArrayInputStream input = new ByteArrayInputStream(schema.getBytes(StandardCharsets.ISO_8859_1))) {
            return factory.newDocumentBuilder().parse(input);
        }
    }

    private static void assertSchema(XmlSchema schema) {
        assertEquals("ISO-8859-1", schema.getInputEncoding());
        assertEquals("urn:encoding", schema.getTargetNamespace());
        XmlSchemaElement element = schema.getElementByName(new QName("urn:encoding", "greeting"));
        assertNotNull(element);
        assertEquals(Constants.XSD_STRING, element.getSchemaTypeName());
    }
}
