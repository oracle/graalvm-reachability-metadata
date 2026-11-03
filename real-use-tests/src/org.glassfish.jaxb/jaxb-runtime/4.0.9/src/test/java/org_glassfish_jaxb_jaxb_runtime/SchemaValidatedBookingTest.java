/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb_jaxb_runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.UnmarshalException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.ValidationEventHandler;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises JAXB runtime's schema-validation subsystem for an inbound booking workflow.
public class SchemaValidatedBookingTest {

    private static final String BOOKING_SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="booking">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="guest" type="xs:string"/>
                    <xs:element name="nights" minOccurs="1">
                      <xs:simpleType>
                        <xs:restriction base="xs:int">
                          <xs:minInclusive value="1"/>
                        </xs:restriction>
                      </xs:simpleType>
                    </xs:element>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    @Test
    @Timeout(60)
    void validatesInboundBookingsBeforeBuildingApplicationObjects() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Booking.class);
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = schemaFactory.newSchema(new StreamSource(new StringReader(BOOKING_SCHEMA)));
        Unmarshaller unmarshaller = context.createUnmarshaller();
        unmarshaller.setSchema(schema);

        Booking booking = assertInstanceOf(Booking.class, unmarshaller.unmarshal(new StringReader(
                "<booking><guest>Rhea</guest><nights>3</nights></booking>")));
        assertEquals("Rhea", booking.guest);
        assertEquals(3, booking.nights);

        AtomicInteger validationEvents = new AtomicInteger();
        ValidationEventHandler handler = event -> {
            validationEvents.incrementAndGet();
            return false;
        };
        unmarshaller.setEventHandler(handler);

        assertThrows(UnmarshalException.class, () -> unmarshaller.unmarshal(new StringReader(
                "<booking><guest>Rhea</guest><nights>0</nights></booking>")));
        assertEquals(1, validationEvents.get());
    }

    @XmlRootElement(name = "booking")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Booking {

        @XmlElement(required = true)
        private String guest;

        @XmlElement(required = true)
        private int nights;

        public Booking() {
        }
    }
}
