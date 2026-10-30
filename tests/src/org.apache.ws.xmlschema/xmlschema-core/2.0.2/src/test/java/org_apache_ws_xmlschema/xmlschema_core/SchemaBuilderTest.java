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
import org.apache.ws.commons.schema.XmlSchemaIdentityConstraint;
import org.apache.ws.commons.schema.XmlSchemaKey;
import org.apache.ws.commons.schema.XmlSchemaKeyref;
import org.apache.ws.commons.schema.XmlSchemaUnique;
import org.apache.xerces.jaxp.DocumentBuilderFactoryImpl;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import javax.xml.namespace.QName;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SchemaBuilderTest {
    @Test
    void readsKeysReferencesAndCompositeUniquenessConstraints() throws Exception {
        String source = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:shop="urn:shop" targetNamespace="urn:shop"
                           elementFormDefault="qualified">
                    <xs:element name="catalog">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="product" minOccurs="0" maxOccurs="unbounded">
                                    <xs:complexType>
                                        <xs:attribute name="id" type="xs:string" use="required"/>
                                        <xs:attribute name="region" type="xs:string"/>
                                        <xs:attribute name="sku" type="xs:string"/>
                                    </xs:complexType>
                                </xs:element>
                                <xs:element name="order" minOccurs="0" maxOccurs="unbounded">
                                    <xs:complexType>
                                        <xs:attribute name="productId" type="xs:string"/>
                                    </xs:complexType>
                                </xs:element>
                            </xs:sequence>
                        </xs:complexType>
                        <xs:key name="productKey">
                            <xs:selector xpath="shop:product"/>
                            <xs:field xpath="@id"/>
                        </xs:key>
                        <xs:keyref name="orderedProduct" refer="shop:productKey">
                            <xs:selector xpath="shop:order"/>
                            <xs:field xpath="@productId"/>
                        </xs:keyref>
                        <xs:unique name="regionalSku">
                            <xs:selector xpath="shop:product"/>
                            <xs:field xpath="@region"/>
                            <xs:field xpath="@sku"/>
                        </xs:unique>
                    </xs:element>
                </xs:schema>
                """;
        DocumentBuilderFactoryImpl factory = new DocumentBuilderFactoryImpl();
        factory.setNamespaceAware(true);
        XmlSchemaCollection collection = new XmlSchemaCollection();
        XmlSchema schema;
        try (StringReader reader = new StringReader(source)) {
            schema = collection.read(factory.newDocumentBuilder().parse(new InputSource(reader)));
        }

        XmlSchemaElement catalog = schema.getElementByName(new QName("urn:shop", "catalog"));
        assertNotNull(catalog);
        List<XmlSchemaIdentityConstraint> constraints = catalog.getConstraints();
        assertEquals(3, constraints.size());

        XmlSchemaKey key = assertInstanceOf(XmlSchemaKey.class, constraints.get(0));
        assertEquals("productKey", key.getName());
        assertEquals("shop:product", key.getSelector().getXPath());
        assertEquals(1, key.getFields().size());
        assertEquals("@id", key.getFields().get(0).getXPath());

        XmlSchemaKeyref reference = assertInstanceOf(XmlSchemaKeyref.class, constraints.get(1));
        assertEquals("orderedProduct", reference.getName());
        assertEquals(new QName("urn:shop", "productKey"), reference.getRefer());
        assertEquals("shop:order", reference.getSelector().getXPath());
        assertEquals(1, reference.getFields().size());
        assertEquals("@productId", reference.getFields().get(0).getXPath());

        XmlSchemaUnique unique = assertInstanceOf(XmlSchemaUnique.class, constraints.get(2));
        assertEquals("regionalSku", unique.getName());
        assertEquals("shop:product", unique.getSelector().getXPath());
        assertEquals(2, unique.getFields().size());
        assertEquals("@region", unique.getFields().get(0).getXPath());
        assertEquals("@sku", unique.getFields().get(1).getXPath());
    }
}
