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
import org.apache.jackrabbit.webdav.client.methods.LockMethod;
import org.apache.jackrabbit.webdav.lock.Scope;
import org.apache.jackrabbit.webdav.lock.Type;
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

public class LockMethodTest {
    @Test
    void createsLockRequestWithHeadersAndSerializedLockInfo() throws Exception {
        LockMethod method = new LockMethod(
                "/repository/document.txt",
                Scope.EXCLUSIVE,
                Type.WRITE,
                "alice",
                120_000L,
                false);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_LOCK);

        Header timeoutHeader = method.getRequestHeader(DavConstants.HEADER_TIMEOUT);
        assertThat(timeoutHeader).isNotNull();
        assertThat(timeoutHeader.getValue()).isEqualTo("Second-120");

        Header depthHeader = method.getRequestHeader(DavConstants.HEADER_DEPTH);
        assertThat(depthHeader).isNotNull();
        assertThat(depthHeader.getValue()).isEqualTo("0");

        Document requestDocument = parseRequestEntity(method.getRequestEntity());
        Element lockInfoElement = requestDocument.getDocumentElement();
        assertThat(DomUtil.matches(lockInfoElement, DavConstants.XML_LOCKINFO, DavConstants.NAMESPACE)).isTrue();

        Element lockScopeElement = DomUtil.getChildElement(
                lockInfoElement,
                DavConstants.XML_LOCKSCOPE,
                DavConstants.NAMESPACE);
        assertThat(lockScopeElement).isNotNull();
        assertThat(DomUtil.hasChildElement(
                lockScopeElement,
                DavConstants.XML_EXCLUSIVE,
                DavConstants.NAMESPACE)).isTrue();

        Element lockTypeElement = DomUtil.getChildElement(
                lockInfoElement,
                DavConstants.XML_LOCKTYPE,
                DavConstants.NAMESPACE);
        assertThat(lockTypeElement).isNotNull();
        assertThat(DomUtil.hasChildElement(
                lockTypeElement,
                DavConstants.XML_WRITE,
                DavConstants.NAMESPACE)).isTrue();

        assertThat(DomUtil.getChildText(lockInfoElement, DavConstants.XML_OWNER, DavConstants.NAMESPACE))
                .isEqualTo("alice");
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
