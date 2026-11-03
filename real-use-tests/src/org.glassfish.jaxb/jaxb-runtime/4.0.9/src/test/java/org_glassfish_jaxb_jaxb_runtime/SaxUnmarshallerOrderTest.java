/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb_jaxb_runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.StringReader;
import java.util.List;
import javax.xml.parsers.SAXParserFactory;
import jakarta.xml.bind.UnmarshallerHandler;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.glassfish.jaxb.runtime.api.JAXBRIContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

/// Exercises JAXB runtime's SAX unmarshaller-handler subsystem for a streaming order intake workflow.
public class SaxUnmarshallerOrderTest {

    @Test
    @Timeout(60)
    void buildsAnOrderFromSaxEventsWithoutBufferingTheDocument() throws Exception {
        JAXBRIContext context = JAXBRIContext.newInstance(
                new Class<?>[] {Order.class}, List.of(), null, null, false, null);
        UnmarshallerHandler handler = context.createUnmarshaller().getUnmarshallerHandler();
        String xml = """
                <order>
                  <number>PO-1042</number>
                  <customer>Rhea</customer>
                  <item><sku>keyboard</sku><quantity>2</quantity></item>
                  <item><sku>mouse</sku><quantity>1</quantity></item>
                </order>
                """;

        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        parserFactory.setNamespaceAware(true);
        XMLReader reader = parserFactory.newSAXParser().getXMLReader();
        reader.setContentHandler(handler);
        reader.parse(new InputSource(new StringReader(xml)));

        Order order = assertInstanceOf(Order.class, handler.getResult());
        assertEquals("PO-1042", order.number);
        assertEquals("Rhea", order.customer);
        assertEquals(List.of("keyboard", "mouse"),
                order.items.stream().map(item -> item.sku).toList());
        assertEquals(List.of(2, 1), order.items.stream().map(item -> item.quantity).toList());
    }

    @XmlRootElement(name = "order")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Order {

        private String number;
        private String customer;

        @XmlElement(name = "item")
        private List<Item> items;

        public Order() {
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Item {

        private String sku;
        private int quantity;

        public Item() {
        }
    }
}
