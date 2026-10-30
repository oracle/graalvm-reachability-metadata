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
import org.junit.jupiter.api.Test;

import javax.xml.namespace.QName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class Xmlschema_coreTest {
    @Test
    void resolvesReferencesToProgrammaticallyRegisteredElements() {
        XmlSchemaCollection collection = new XmlSchemaCollection();
        XmlSchema schema = new XmlSchema("urn:inventory", collection);
        XmlSchemaElement product = new XmlSchemaElement(schema, true);
        product.setName("product");
        product.setSchemaTypeName(Constants.XSD_STRING);

        QName productName = new QName("urn:inventory", "product");
        XmlSchemaElement reference = new XmlSchemaElement(schema, false);
        reference.getRef().setTargetQName(productName);

        assertSame(product, schema.getElementByName(productName));
        assertSame(product, collection.getElementByQName(productName));
        assertSame(product, reference.getRef().getTarget());
        assertEquals(Constants.XSD_STRING, reference.getRef().getTarget().getSchemaTypeName());
    }
}
