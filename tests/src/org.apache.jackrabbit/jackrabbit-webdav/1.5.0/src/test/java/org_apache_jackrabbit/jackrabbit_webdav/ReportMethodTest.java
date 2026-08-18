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
import org.apache.jackrabbit.webdav.client.methods.ReportMethod;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.apache.jackrabbit.webdav.version.report.ReportType;
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

public class ReportMethodTest {
    @Test
    void createsReportRequestWithDepthAndReportBody() throws Exception {
        DavPropertyNameSet properties = new DavPropertyNameSet();
        properties.add(DeltaVConstants.COMMENT);
        properties.add(DavPropertyName.DISPLAYNAME);
        ReportInfo reportInfo = new ReportInfo(ReportType.VERSION_TREE, DavConstants.DEPTH_1, properties);

        ReportMethod method = new ReportMethod("/repository/default/file.txt", reportInfo);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_REPORT);
        assertThat(method.getPath()).isEqualTo("/repository/default/file.txt");

        Header depthHeader = method.getRequestHeader(DavConstants.HEADER_DEPTH);
        assertThat(depthHeader).isNotNull();
        assertThat(depthHeader.getValue()).isEqualTo("1");

        Document requestDocument = parseRequestEntity(method.getRequestEntity());
        Element reportElement = requestDocument.getDocumentElement();
        assertThat(DomUtil.matches(
                reportElement,
                DeltaVConstants.XML_VERSION_TREE,
                DeltaVConstants.NAMESPACE)).isTrue();

        Element propElement = DomUtil.getChildElement(
                reportElement,
                DavConstants.XML_PROP,
                DavConstants.NAMESPACE);
        assertThat(propElement).isNotNull();
        assertThat(DomUtil.hasChildElement(
                propElement,
                DeltaVConstants.COMMENT.getName(),
                DeltaVConstants.COMMENT.getNamespace())).isTrue();
        assertThat(DomUtil.hasChildElement(
                propElement,
                DavConstants.PROPERTY_DISPLAYNAME,
                DavConstants.NAMESPACE)).isTrue();
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
