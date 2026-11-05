/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb_jaxb_runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.glassfish.jaxb.runtime.api.Bridge;
import org.glassfish.jaxb.runtime.api.JAXBRIContext;
import org.glassfish.jaxb.runtime.api.TypeReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises JAXB runtime's typed-bridge subsystem for a shipment message exchange.
public class TypedBridgeShipmentTest {

    private static final String SHIPPING_NAMESPACE = "urn:shipping";

    @Test
    @Timeout(60)
    void marshalsAndUnmarshalsTypedMessagesThroughAStreamingBridge() throws Exception {
        TypeReference shipmentType = new TypeReference(
                new QName(SHIPPING_NAMESPACE, "shipment"), Shipment.class);
        JAXBRIContext context = JAXBRIContext.newInstance(
                new Class<?>[] {Shipment.class}, List.of(shipmentType), null, null, false, null);
        Bridge<Shipment> bridge = context.createBridge(shipmentType);

        Shipment shipment = new Shipment("TRK-2048", List.of("keyboard", "mouse"));
        StringWriter output = new StringWriter();
        XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
        bridge.marshal(shipment, writer);
        writer.close();

        String xml = output.toString();
        assertTrue(xml.contains("shipment"));
        assertTrue(xml.contains("TRK-2048"));
        assertTrue(xml.contains("<contents>keyboard</contents>"));

        XMLStreamReader reader = XMLInputFactory.newFactory()
                .createXMLStreamReader(new StringReader(xml));
        Shipment restored = bridge.unmarshal(reader);
        reader.close();

        assertEquals("TRK-2048", restored.trackingNumber);
        assertEquals(List.of("keyboard", "mouse"), restored.contents);
    }

    @XmlRootElement(name = "shipment", namespace = SHIPPING_NAMESPACE)
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Shipment {

        @XmlElement(required = true)
        private String trackingNumber;

        @XmlElement(name = "contents", required = true)
        private List<String> contents;

        public Shipment() {
        }

        private Shipment(String trackingNumber, List<String> contents) {
            this.trackingNumber = trackingNumber;
            this.contents = contents;
        }
    }
}
