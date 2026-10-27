/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_messaging_saaj.saaj_impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import javax.xml.namespace.QName;

import jakarta.xml.soap.AttachmentPart;
import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.MimeHeaders;
import jakarta.xml.soap.Node;
import jakarta.xml.soap.SOAPBody;
import jakarta.xml.soap.SOAPConnection;
import jakarta.xml.soap.SOAPConnectionFactory;
import jakarta.xml.soap.SOAPConstants;
import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPFactory;
import jakarta.xml.soap.SOAPMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class Saaj_implTest {
    @Test
    void createsAndRoundTripsSoapMessage() throws Exception {
        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage message = messageFactory.createMessage();
        SOAPBody body = message.getSOAPBody();
        SOAPElement request = body.addChildElement("request", "demo", "urn:demo");
        request.addChildElement("value", "demo", "urn:demo").addTextNode("payload");
        message.getSOAPHeader().addHeaderElement(new QName("urn:demo", "trace", "demo"))
                .addTextNode("trace-id");
        message.saveChanges();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        message.writeTo(output);
        MimeHeaders headers = message.getMimeHeaders();
        SOAPMessage parsed = messageFactory.createMessage(
                headers, new ByteArrayInputStream(output.toByteArray()));

        Iterator<Node> requests = parsed.getSOAPBody()
                .getChildElements(new QName("urn:demo", "request"));
        assertThat(requests.hasNext()).isTrue();
        SOAPElement parsedRequest = (SOAPElement) requests.next();
        assertThat(((SOAPElement) parsedRequest
                .getChildElements(new QName("urn:demo", "value")).next()).getValue())
                .isEqualTo("payload");
        Iterator<Node> traces = parsed.getSOAPHeader()
                .getChildElements(new QName("urn:demo", "trace"));
        assertThat(traces.hasNext()).isTrue();
        assertThat(((SOAPElement) traces.next()).getValue()).isEqualTo("trace-id");
    }

    @Test
    void roundTripsMimeAttachmentThroughMessageApi() throws Exception {
        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage message = messageFactory.createMessage();
        AttachmentPart attachment = message.createAttachmentPart();
        byte[] payload = "attachment payload".getBytes(StandardCharsets.UTF_8);
        attachment.setContentId("<payload@example.test>");
        attachment.setRawContentBytes(payload, 0, payload.length, "text/plain");
        message.addAttachmentPart(attachment);
        message.saveChanges();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        message.writeTo(output);
        SOAPMessage parsed = messageFactory.createMessage(
                message.getMimeHeaders(), new ByteArrayInputStream(output.toByteArray()));

        assertThat(parsed.countAttachments()).isEqualTo(1);
        AttachmentPart parsedAttachment = (AttachmentPart) parsed.getAttachments().next();
        assertThat(parsedAttachment.getContentId()).isEqualTo("<payload@example.test>");
        assertThat(parsedAttachment.getContentType()).isEqualTo("text/plain");
        assertThat(parsedAttachment.getRawContentBytes()).isEqualTo(payload);
    }

    @Test
    void createsSoap12FaultThroughFactoryApi() throws Exception {
        MessageFactory messageFactory = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
        SOAPMessage message = messageFactory.createMessage();

        message.getSOAPBody().addFault().addFaultReasonText("invalid request", Locale.ENGLISH);
        message.saveChanges();

        assertThat(message.getSOAPBody().hasFault()).isTrue();
        assertThat(message.getSOAPBody().getFault().getFaultReasonTexts().next())
                .isEqualTo("invalid request");
        assertThat(message.getSOAPPart().getEnvelope().getElementQName().getNamespaceURI())
                .isEqualTo(SOAPConstants.URI_NS_SOAP_1_2_ENVELOPE);
    }

    @Test
    void createsSoapElementWithSoapFactory() throws Exception {
        SOAPFactory soapFactory = SOAPFactory.newInstance();
        SOAPMessage message = MessageFactory.newInstance().createMessage();
        SOAPElement element = soapFactory.createElement(
                new QName("urn:demo", "operation", "demo"));
        element.addChildElement("argument", "demo", "urn:demo").addTextNode("42");
        message.getSOAPBody().addChildElement(element);
        message.saveChanges();

        Iterator<Node> operations = message.getSOAPBody()
                .getChildElements(new QName("urn:demo", "operation"));
        assertThat(operations.hasNext()).isTrue();
        assertThat(((SOAPElement) ((SOAPElement) operations.next())
                .getChildElements(new QName("urn:demo", "argument")).next()).getValue())
                .isEqualTo("42");
    }

    @Test
    void createsAndClosesSoapConnection() throws Exception {
        SOAPConnectionFactory connectionFactory = SOAPConnectionFactory.newInstance();
        SOAPConnection connection = connectionFactory.createConnection();

        assertThat(connection).isNotNull();
        connection.close();
    }

    @Test
    void callsSoapEndpointThroughSoapConnection() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/soap", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                    + "<soap:Body><reply xmlns=\"urn:demo\">accepted</reply></soap:Body>"
                    + "</soap:Envelope>").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/xml; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();

        try (SOAPConnection connection = SOAPConnectionFactory.newInstance().createConnection()) {
            SOAPMessage request = MessageFactory.newInstance().createMessage();
            SOAPMessage response = connection.call(
                    request, new URL("http://localhost:" + server.getAddress().getPort() + "/soap"));

            Iterator<Node> replies = response.getSOAPBody()
                    .getChildElements(new QName("urn:demo", "reply"));
            assertThat(replies.hasNext()).isTrue();
            assertThat(((SOAPElement) replies.next()).getValue()).isEqualTo("accepted");
        } finally {
            server.stop(0);
        }
    }
}
