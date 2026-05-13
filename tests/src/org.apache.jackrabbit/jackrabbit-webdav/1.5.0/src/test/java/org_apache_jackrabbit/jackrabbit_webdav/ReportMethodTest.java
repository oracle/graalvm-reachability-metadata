/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.ReportMethod;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class ReportMethodTest {
    @Test
    public void constructorCreatesDepthZeroReportRequest() throws Exception {
        ExposedReportMethod method = new ExposedReportMethod(
                "http://localhost:8080/repository/default",
                reportInfo(DavConstants.DEPTH_0));

        Document requestBody = requestBody(method);
        Element report = requestBody.getDocumentElement();

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_REPORT);
        assertThat(method.getPath()).isEqualTo("/repository/default");
        assertThat(method.getRequestHeader("Depth").getValue()).isEqualTo("0");
        assertThat(method.isSuccessfulStatus(200)).isTrue();
        assertThat(method.isSuccessfulStatus(207)).isTrue();
        assertThat(report.getLocalName()).isEqualTo("version-tree");
        assertThat(report.getNamespaceURI()).isEqualTo("DAV:");
    }

    @Test
    public void depthOneReportRequiresMultiStatusResponse() throws Exception {
        ExposedReportMethod method = new ExposedReportMethod(
                "http://localhost:8080/repository/default/collection",
                reportInfo(DavConstants.DEPTH_1));

        assertThat(method.getRequestHeader("Depth").getValue()).isEqualTo("1");
        assertThat(method.isSuccessfulStatus(200)).isFalse();
        assertThat(method.isSuccessfulStatus(207)).isTrue();
    }

    private static ReportInfo reportInfo(int depth) throws Exception {
        Document document = newDocument();
        Element reportElement = document.createElementNS(DavConstants.NAMESPACE.getURI(), "D:version-tree");
        document.appendChild(reportElement);
        return new ReportInfo(reportElement, depth);
    }

    private static Document requestBody(ReportMethod method) throws Exception {
        RequestEntity requestEntity = method.getRequestEntity();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        requestEntity.writeRequest(output);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(output.toByteArray()));
    }

    private static Document newDocument() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().newDocument();
    }

    private static final class ExposedReportMethod extends ReportMethod {
        private ExposedReportMethod(String uri, ReportInfo reportInfo) throws IOException {
            super(uri, reportInfo);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
