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
import org.apache.jackrabbit.webdav.client.methods.MergeMethod;
import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.MergeInfo;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.xml.ElementIterator;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class MergeMethodTest {
    @Test
    void createsMergeRequestWithSerializedMergeInfo() throws Exception {
        Document document = newDocumentBuilder().newDocument();
        MergeInfo mergeInfo = new MergeInfo(MergeInfo.createMergeElement(
                new String[] {"/repository/workspace", "/repository/baseline"},
                true,
                true,
                document));

        MergeMethod method = new MergeMethod("/repository/versionable-node", mergeInfo);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_MERGE);

        Document requestDocument = parseRequestEntity(method.getRequestEntity());
        Element mergeElement = requestDocument.getDocumentElement();
        assertThat(DomUtil.matches(mergeElement, DeltaVConstants.XML_MERGE, DeltaVConstants.NAMESPACE)).isTrue();
        assertThat(DomUtil.hasChildElement(
                mergeElement,
                DeltaVConstants.XML_N0_AUTO_MERGE,
                DeltaVConstants.NAMESPACE)).isTrue();
        assertThat(DomUtil.hasChildElement(
                mergeElement,
                DeltaVConstants.XML_N0_CHECKOUT,
                DeltaVConstants.NAMESPACE)).isTrue();

        Element sourceElement = DomUtil.getChildElement(
                mergeElement,
                DavConstants.XML_SOURCE,
                DavConstants.NAMESPACE);
        assertThat(sourceElement).isNotNull();
        assertThat(hrefs(sourceElement)).containsExactly("/repository/workspace", "/repository/baseline");
    }

    private static List<String> hrefs(Element sourceElement) {
        ElementIterator iterator = DomUtil.getChildren(
                sourceElement,
                DavConstants.XML_HREF,
                DavConstants.NAMESPACE);
        List<String> hrefs = new ArrayList<String>();
        while (iterator.hasNext()) {
            hrefs.add(DomUtil.getTextTrim(iterator.nextElement()));
        }
        return hrefs;
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
