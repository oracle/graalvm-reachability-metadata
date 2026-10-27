/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.OrderPatchMethod;
import org.apache.jackrabbit.webdav.ordering.OrderingConstants;
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

public class OrderPatchMethodTest {
    @Test
    void createsOrderPatchRequestThatMovesMemberBeforeTarget() throws Exception {
        OrderPatchMethod method = new OrderPatchMethod(
                "/repository/ordered-collection",
                OrderingConstants.ORDERING_TYPE_CUSTOM,
                "chapter-2",
                "chapter-1",
                true);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_ORDERPATCH);

        Document requestDocument = parseRequestEntity(method.getRequestEntity());
        Element orderPatchElement = requestDocument.getDocumentElement();
        assertThat(DomUtil.matches(
                orderPatchElement,
                OrderingConstants.XML_ORDERPATCH,
                OrderingConstants.NAMESPACE)).isTrue();

        Element orderingTypeElement = DomUtil.getChildElement(
                orderPatchElement,
                OrderingConstants.XML_ORDERING_TYPE,
                OrderingConstants.NAMESPACE);
        assertThat(orderingTypeElement).isNotNull();
        assertThat(DomUtil.getChildText(
                orderingTypeElement,
                DavConstants.XML_HREF,
                DavConstants.NAMESPACE)).isEqualTo(OrderingConstants.ORDERING_TYPE_CUSTOM);

        Element orderMemberElement = DomUtil.getChildElement(
                orderPatchElement,
                OrderingConstants.XML_ORDER_MEMBER,
                OrderingConstants.NAMESPACE);
        assertThat(orderMemberElement).isNotNull();
        assertThat(DomUtil.getChildText(
                orderMemberElement,
                OrderingConstants.XML_SEGMENT,
                OrderingConstants.NAMESPACE)).isEqualTo("chapter-2");

        Element positionElement = DomUtil.getChildElement(
                orderMemberElement,
                OrderingConstants.XML_POSITION,
                OrderingConstants.NAMESPACE);
        assertThat(positionElement).isNotNull();
        Element beforeElement = DomUtil.getChildElement(
                positionElement,
                OrderingConstants.XML_BEFORE,
                OrderingConstants.NAMESPACE);
        assertThat(beforeElement).isNotNull();
        assertThat(DomUtil.getChildText(
                beforeElement,
                OrderingConstants.XML_SEGMENT,
                OrderingConstants.NAMESPACE)).isEqualTo("chapter-1");
    }

    @Test
    void createsOrderPatchRequestThatMovesMemberLast() throws Exception {
        OrderPatchMethod method = new OrderPatchMethod(
                "/repository/ordered-collection",
                OrderingConstants.ORDERING_TYPE_UNORDERED,
                "appendix",
                false);

        Document requestDocument = parseRequestEntity(method.getRequestEntity());
        Element positionElement = DomUtil.getChildElement(
                DomUtil.getChildElement(
                        requestDocument.getDocumentElement(),
                        OrderingConstants.XML_ORDER_MEMBER,
                        OrderingConstants.NAMESPACE),
                OrderingConstants.XML_POSITION,
                OrderingConstants.NAMESPACE);
        assertThat(positionElement).isNotNull();
        assertThat(DomUtil.hasChildElement(
                positionElement,
                OrderingConstants.XML_LAST,
                OrderingConstants.NAMESPACE)).isTrue();
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
