/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises RESTEasy's JAXB XML entity binding subsystem to submit a supplier purchase order.
public class XmlSupplierPurchaseOrderTest {

    @Test
    @Timeout(60)
    public void sendsNestedOrderLinesAndReadsTheSuppliersXmlAcknowledgement()
            throws IOException, ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder parser = factory.newDocumentBuilder();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/supplier/orders", exchange -> acceptOrder(exchange, parser));
        server.start();
        try {
            PurchaseOrder order = new PurchaseOrder();
            order.reference = "PO-42";
            order.recipient = "Caf\u00e9 & Sons";
            order.lines = List.of(new OrderLine("blue-widget", 3), new OrderLine("red-widget", 5));

            ResteasyClientBuilder builder = (ResteasyClientBuilder) ClientBuilder.newBuilder();
            builder.connectTimeout(10, TimeUnit.SECONDS);
            builder.readTimeout(10, TimeUnit.SECONDS);
            try (ResteasyClient client = builder.build();
                    Response response = client.target("http://127.0.0.1:" + server.getAddress().getPort())
                            .path("supplier/orders").request(MediaType.APPLICATION_XML_TYPE)
                            .post(Entity.entity(order, MediaType.APPLICATION_XML_TYPE))) {
                assertEquals(201, response.getStatus());
                assertEquals(MediaType.APPLICATION_XML_TYPE, response.getMediaType());
                Acknowledgement receipt = response.readEntity(Acknowledgement.class);
                assertEquals("PO-42", receipt.reference);
                assertEquals("supplier-1042", receipt.supplierOrder);
                assertEquals("Caf\u00e9 & Sons", receipt.recipient);
                assertEquals("accepted", receipt.status);
                assertEquals(8, receipt.acceptedUnits);
            }
        } finally {
            server.stop(0);
        }
    }

    @XmlRootElement(name = "purchase-order")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class PurchaseOrder {
        @XmlAttribute
        public String reference;
        public String recipient;
        @XmlElementWrapper(name = "lines")
        @XmlElement(name = "line")
        public List<OrderLine> lines;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class OrderLine {
        @XmlAttribute
        public String sku;
        public int quantity;

        public OrderLine() {
        }

        public OrderLine(String sku, int quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }
    }

    @XmlRootElement(name = "acknowledgement")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Acknowledgement {
        @XmlAttribute
        public String reference;
        @XmlElement(name = "supplier-order")
        public String supplierOrder;
        public String recipient;
        public String status;
        @XmlElement(name = "accepted-units")
        public int acceptedUnits;
    }

    private static void acceptOrder(HttpExchange exchange, DocumentBuilder parser) throws IOException {
        try (exchange) {
            String contentType = exchange.getRequestHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
            if (!"POST".equals(exchange.getRequestMethod()) || contentType == null
                    || !MediaType.APPLICATION_XML_TYPE.isCompatible(MediaType.valueOf(contentType))
                    || !MediaType.APPLICATION_XML.equals(exchange.getRequestHeaders().getFirst(HttpHeaders.ACCEPT))) {
                respond(exchange, 415, "Supplier requires XML purchase orders");
                return;
            }
            Element order;
            try {
                order = parser.parse(exchange.getRequestBody()).getDocumentElement();
            } catch (SAXException invalidXml) {
                respond(exchange, 400, "Invalid purchase order XML");
                return;
            }
            NodeList recipients = order.getElementsByTagName("recipient");
            NodeList lines = order.getElementsByTagName("line");
            if (!"purchase-order".equals(order.getTagName()) || !"PO-42".equals(order.getAttribute("reference"))
                    || recipients.getLength() != 1 || !"Caf\u00e9 & Sons".equals(recipients.item(0).getTextContent())
                    || order.getElementsByTagName("lines").getLength() != 1 || lines.getLength() != 2
                    || !matchesLine((Element) lines.item(0), "blue-widget", "3")
                    || !matchesLine((Element) lines.item(1), "red-widget", "5")) {
                respond(exchange, 422, "Purchase order details did not match the requested supply");
                return;
            }
            respond(exchange, 201, """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <acknowledgement reference="PO-42">
                      <supplier-order>supplier-1042</supplier-order>
                      <recipient>Caf\u00e9 &amp; Sons</recipient>
                      <status>accepted</status>
                      <accepted-units>8</accepted-units>
                    </acknowledgement>
                    """);
        }
    }

    private static boolean matchesLine(Element line, String sku, String quantity) {
        NodeList quantities = line.getElementsByTagName("quantity");
        return sku.equals(line.getAttribute("sku")) && quantities.getLength() == 1
                && quantity.equals(quantities.item(0).getTextContent());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE,
                status == 201 ? MediaType.APPLICATION_XML : "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
