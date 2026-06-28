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
import org.apache.jackrabbit.webdav.client.methods.UpdateMethod;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.UpdateInfo;
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

public class UpdateMethodTest {
    @Test
    void createsUpdateRequestWithSerializedUpdateInfo() throws Exception {
        DavPropertyNameSet properties = new DavPropertyNameSet();
        properties.add(DeltaVConstants.COMMENT);
        properties.add(DavPropertyName.DISPLAYNAME);
        UpdateInfo updateInfo = new UpdateInfo(
                new String[] {"/repository/versions/1.0", "/repository/versions/1.1"},
                UpdateInfo.UPDATE_BY_VERSION,
                properties);

        UpdateMethod method = new UpdateMethod("/repository/default/file.txt", updateInfo);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_UPDATE);
        assertThat(method.getPath()).isEqualTo("/repository/default/file.txt");

        Document requestDocument = parseRequestEntity(method.getRequestEntity());
        Element updateElement = requestDocument.getDocumentElement();
        assertThat(DomUtil.matches(
                updateElement,
                DeltaVConstants.XML_UPDATE,
                DeltaVConstants.NAMESPACE)).isTrue();

        Element versionElement = DomUtil.getChildElement(
                updateElement,
                DeltaVConstants.XML_VERSION,
                DeltaVConstants.NAMESPACE);
        assertThat(versionElement).isNotNull();
        assertThat(hrefs(versionElement)).containsExactly("/repository/versions/1.0", "/repository/versions/1.1");

        Element propElement = DomUtil.getChildElement(
                updateElement,
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
