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
import org.apache.jackrabbit.webdav.client.methods.BaselineControlMethod;
import org.apache.jackrabbit.webdav.version.DeltaVConstants;
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

public class BaselineControlMethodTest {
    @Test
    void createsBaselineControlRequestWithBaselineHref() throws Exception {
        BaselineControlMethod method = new BaselineControlMethod(
                "/repository/workspace",
                "/repository/baselines/release-1");

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_BASELINE_CONTROL);

        Document requestDocument = parseRequestEntity(method.getRequestEntity());
        Element baselineControlElement = requestDocument.getDocumentElement();
        assertThat(DomUtil.matches(
                baselineControlElement,
                "baseline-control",
                DeltaVConstants.NAMESPACE)).isTrue();
        assertThat(DomUtil.getChildText(
                baselineControlElement,
                DavConstants.XML_HREF,
                DavConstants.NAMESPACE)).isEqualTo("/repository/baselines/release-1");
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
