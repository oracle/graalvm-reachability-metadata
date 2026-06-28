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
import org.apache.jackrabbit.webdav.client.methods.LabelMethod;
import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.LabelInfo;
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

public class LabelMethodTest {
    @Test
    void createsLabelRequestWithDepthHeaderAndSerializedLabelInfo() throws Exception {
        LabelMethod method = new LabelMethod(
                "/repository/versionable-node",
                "release-candidate",
                LabelInfo.TYPE_ADD,
                DavConstants.DEPTH_1);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_LABEL);

        Header depthHeader = method.getRequestHeader(DavConstants.HEADER_DEPTH);
        assertThat(depthHeader).isNotNull();
        assertThat(depthHeader.getValue()).isEqualTo("1");

        Document requestDocument = parseRequestEntity(method.getRequestEntity());
        Element labelElement = requestDocument.getDocumentElement();
        assertThat(DomUtil.matches(labelElement, DeltaVConstants.XML_LABEL, DeltaVConstants.NAMESPACE)).isTrue();

        Element addElement = DomUtil.getChildElement(
                labelElement,
                DeltaVConstants.XML_LABEL_ADD,
                DeltaVConstants.NAMESPACE);
        assertThat(addElement).isNotNull();
        assertThat(DomUtil.getChildText(
                addElement,
                DeltaVConstants.XML_LABEL_NAME,
                DeltaVConstants.NAMESPACE)).isEqualTo("release-candidate");
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
