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
import org.apache.ws.commons.schema.extensions.ExtensionRegistry;
import org.apache.xerces.jaxp.DocumentBuilderFactoryImpl;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import javax.xml.namespace.QName;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class XmlSchemaCollectionTest {
    @Test
    void loadsConfiguredRegistryAndDeserializesExtensionAttribute() throws Exception {
        String property = Constants.SystemConstants.EXTENSION_REGISTRY_KEY;
        String previous = System.getProperty(property);
        try {
            System.setProperty(property, AttributeExtensionRegistry.class.getName());
            XmlSchemaCollection collection = new XmlSchemaCollection();
            assertInstanceOf(AttributeExtensionRegistry.class, collection.getExtReg());

            String source = """
                    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                               xmlns:ext="urn:order:extensions" targetNamespace="urn:order">
                        <xs:element name="order" type="xs:string" ext:category="priority"/>
                    </xs:schema>
                    """;
            DocumentBuilderFactoryImpl factory = new DocumentBuilderFactoryImpl();
            factory.setNamespaceAware(true);
            try (StringReader reader = new StringReader(source)) {
                XmlSchema schema = collection.read(factory.newDocumentBuilder().parse(new InputSource(reader)));
                QName elementName = new QName("urn:order", "order");
                XmlSchemaElement element = schema.getElementByName(elementName);

                assertNotNull(element);
                assertEquals(Constants.XSD_STRING, element.getSchemaTypeName());
                assertNotNull(element.getMetaInfoMap());
                assertEquals("priority", element.getMetaInfoMap().get("category"));
                assertSame(element, collection.getElementByQName(elementName));
            }
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    public static class AttributeExtensionRegistry extends ExtensionRegistry {
        public AttributeExtensionRegistry() {
            registerDeserializer(new QName("urn:order:extensions", "category"),
                    (schemaObject, name, node) -> schemaObject.addMetaInfo("category", node.getNodeValue()));
        }
    }
}
