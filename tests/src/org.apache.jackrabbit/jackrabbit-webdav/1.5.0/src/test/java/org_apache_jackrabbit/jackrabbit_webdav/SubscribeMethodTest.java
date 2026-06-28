/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.SubscribeMethod;
import org.apache.jackrabbit.webdav.observation.DefaultEventType;
import org.apache.jackrabbit.webdav.observation.EventType;
import org.apache.jackrabbit.webdav.observation.ObservationConstants;
import org.apache.jackrabbit.webdav.observation.SubscriptionInfo;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class SubscribeMethodTest {
    @Test
    void createsSubscribeRequestWithHeadersAndSerializedSubscriptionInfo() throws Exception {
        EventType eventType = DefaultEventType.create("modified", ObservationConstants.NAMESPACE);
        SubscriptionInfo subscriptionInfo = new SubscriptionInfo(new EventType[] {eventType}, false, 30_000L);

        SubscribeMethod method = new SubscribeMethod(
                "/repository/default",
                subscriptionInfo,
                "subscription-123");

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_SUBSCRIBE);
        assertThat(method.getPath()).isEqualTo("/repository/default");

        Header subscriptionHeader = method.getRequestHeader(ObservationConstants.HEADER_SUBSCRIPTIONID);
        assertThat(subscriptionHeader).isNotNull();
        assertThat(subscriptionHeader.getValue()).isEqualTo("<subscription-123>");

        Header timeoutHeader = method.getRequestHeader(DavConstants.HEADER_TIMEOUT);
        assertThat(timeoutHeader).isNotNull();
        assertThat(timeoutHeader.getValue()).isEqualTo("Second-30");

        Header depthHeader = method.getRequestHeader(DavConstants.HEADER_DEPTH);
        assertThat(depthHeader).isNotNull();
        assertThat(depthHeader.getValue()).isEqualTo("0");

        Document requestDocument = parseRequestEntity(method.getRequestEntity());
        Element subscriptionInfoElement = requestDocument.getDocumentElement();
        assertThat(DomUtil.matches(
                subscriptionInfoElement,
                ObservationConstants.XML_SUBSCRIPTIONINFO,
                ObservationConstants.NAMESPACE)).isTrue();

        Element eventTypeElement = DomUtil.getChildElement(
                subscriptionInfoElement,
                ObservationConstants.XML_EVENTTYPE,
                ObservationConstants.NAMESPACE);
        assertThat(eventTypeElement).isNotNull();
        assertThat(DomUtil.hasChildElement(
                eventTypeElement,
                "modified",
                ObservationConstants.NAMESPACE)).isTrue();
        assertThat(DomUtil.hasChildElement(
                subscriptionInfoElement,
                ObservationConstants.XML_NOLOCAL,
                ObservationConstants.NAMESPACE)).isFalse();
    }

    private static Document parseRequestEntity(RequestEntity requestEntity) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        requestEntity.writeRequest(output);
        DocumentBuilder builder = newDocumentBuilder();
        return builder.parse(new InputSource(new ByteArrayInputStream(output.toByteArray())));
    }

    private static DocumentBuilder newDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder();
    }
}
