/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb_jaxb_runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import javax.xml.namespace.QName;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.SchemaOutputResolver;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.glassfish.jaxb.runtime.api.JAXBRIContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises JAXB runtime's schema-generation subsystem for a shipping document model.
public class ShippingSchemaGenerationTest {

    private static final String SHIPPING_NAMESPACE = "urn:shipping";

    @Test
    @Timeout(60)
    void exposesModelNamesAndGeneratesConsumerSchema() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Shipment.class);
        JAXBRIContext runtimeContext = assertInstanceOf(JAXBRIContext.class, context);

        assertEquals(new QName(SHIPPING_NAMESPACE, "shipment"),
                runtimeContext.getElementName(Shipment.class));
        assertTrue(runtimeContext.getKnownNamespaceURIs().contains(SHIPPING_NAMESPACE));

        StringWriter schemaText = new StringWriter();
        SchemaOutputResolver resolver = new SchemaOutputResolver() {

            @Override
            public Result createOutput(String namespaceUri, String suggestedFileName) throws IOException {
                return schemaResult(namespaceUri, schemaText);
            }
        };
        context.generateSchema(resolver);

        String schema = schemaText.toString();
        assertTrue(schema.contains("targetNamespace=\"urn:shipping\""));
        assertTrue(schema.contains("name=\"shipment\""));
        assertTrue(schema.contains("name=\"trackingNumber\""));
        assertTrue(schema.contains("name=\"packages\""));
    }

    private static Result schemaResult(String namespaceUri, StringWriter schemaText) {
        StreamResult result = new StreamResult(schemaText);
        result.setSystemId("https://example.test/schema.xsd");
        return result;
    }

    @XmlRootElement(name = "shipment", namespace = SHIPPING_NAMESPACE)
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Shipment {

        @XmlElement(required = true)
        private String trackingNumber;

        @XmlElement(required = true)
        private List<Package> packages;

        public Shipment() {
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Package {

        @XmlElement(required = true)
        private String contents;

        public Package() {
        }
    }
}
