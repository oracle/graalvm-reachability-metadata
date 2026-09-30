/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_messaging_saaj.saaj_impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.Locale;
import javax.xml.namespace.QName;

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
}
