/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb_jaxb_runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.Document;
import jakarta.xml.bind.Binder;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/// Exercises JAXB runtime's DOM binder subsystem for an order editor workflow.
public class DomBinderOrderTest {

    @Test
    @Timeout(60)
    void synchronizesApplicationObjectsAndAnEditableDom() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Order.class);
        Binder<org.w3c.dom.Node> binder = context.createBinder();
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        Order order = new Order("A-104", "Rhea", List.of(new Item("keyboard", 1)));
        binder.marshal(order, document);

        assertEquals("order", document.getDocumentElement().getNodeName());
        assertEquals("Rhea", document.getElementsByTagName("customer").item(0).getTextContent());

        document.getElementsByTagName("customer").item(0).setTextContent("Mina");
        Order updated = (Order) binder.updateJAXB(document);
        assertNotNull(updated);
        assertEquals("Mina", updated.customer);

        updated.items.get(0).quantity = 3;
        binder.updateXML(updated);
        assertEquals("3", document.getElementsByTagName("quantity").item(0).getTextContent());
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

        private Order(String number, String customer, List<Item> items) {
            this.number = number;
            this.customer = customer;
            this.items = items;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Item {

        private String sku;
        private int quantity;

        public Item() {
        }

        private Item(String sku, int quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }
    }
}
