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
import java.util.Arrays;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.client.methods.PropPatchMethod;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class PropPatchMethodTest {
    @Test
    public void changeListConstructorCreatesSetAndRemoveRequest() throws Exception {
        List changes = Arrays.asList(
                new DefaultDavProperty(DavPropertyName.DISPLAYNAME, "updated-title"),
                DavPropertyName.GETCONTENTTYPE);

        ExposedPropPatchMethod method = new ExposedPropPatchMethod(
                "http://localhost:8080/repository/default/document.txt",
                changes);

        Document requestBody = requestBody(method);
        Element propertyUpdate = requestBody.getDocumentElement();
        Element set = firstElementChild(propertyUpdate);
        Element remove = nextElementSibling(set);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_PROPPATCH);
        assertThat(method.getPath()).isEqualTo("/repository/default/document.txt");
        assertThat(method.isSuccessfulStatus(207)).isTrue();
        assertThat(method.isSuccessfulStatus(200)).isTrue();
        assertThat(method.isSuccessfulStatus(201)).isFalse();
        assertThat(propertyUpdate.getLocalName()).isEqualTo("propertyupdate");
        assertThat(propertyUpdate.getNamespaceURI()).isEqualTo("DAV:");
        assertThat(set.getLocalName()).isEqualTo("set");
        assertThat(remove.getLocalName()).isEqualTo("remove");
        assertThat(elementCount(set, "displayname")).isEqualTo(1);
        assertThat(set.getTextContent()).contains("updated-title");
        assertThat(elementCount(remove, "getcontenttype")).isEqualTo(1);
    }

    @Test
    public void propertySetConstructorCreatesCombinedPropPatchRequest() throws Exception {
        DavPropertySet setProperties = new DavPropertySet();
        setProperties.add(new DefaultDavProperty(DavPropertyName.GETCONTENTLANGUAGE, "en-US"));
        DavPropertyNameSet removeProperties = new DavPropertyNameSet();
        removeProperties.add(DavPropertyName.GETCONTENTLENGTH);

        PropPatchMethod method = new PropPatchMethod(
                "http://localhost:8080/repository/default/document.txt",
                setProperties,
                removeProperties);

        Document requestBody = requestBody(method);
        Element propertyUpdate = requestBody.getDocumentElement();
        Element set = firstElementChild(propertyUpdate);
        Element remove = nextElementSibling(set);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_PROPPATCH);
        assertThat(set.getLocalName()).isEqualTo("set");
        assertThat(remove.getLocalName()).isEqualTo("remove");
        assertThat(elementCount(set, "getcontentlanguage")).isEqualTo(1);
        assertThat(set.getTextContent()).contains("en-US");
        assertThat(elementCount(remove, "getcontentlength")).isEqualTo(1);
    }

    private static Document requestBody(PropPatchMethod method) throws Exception {
        RequestEntity requestEntity = method.getRequestEntity();
        assertThat(requestEntity).isNotNull();
        assertThat(requestEntity.isRepeatable()).isTrue();
        assertThat(requestEntity.getContentType()).startsWith("text/xml; charset=");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        requestEntity.writeRequest(output);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(output.toByteArray()));
    }

    private static Element firstElementChild(Element element) {
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) child;
            }
        }
        return null;
    }

    private static Element nextElementSibling(Element element) {
        for (Node sibling = element.getNextSibling(); sibling != null; sibling = sibling.getNextSibling()) {
            if (sibling.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) sibling;
            }
        }
        return null;
    }

    private static int elementCount(Element parent, String localName) {
        return parent.getElementsByTagNameNS("DAV:", localName).getLength();
    }

    private static final class ExposedPropPatchMethod extends PropPatchMethod {
        private ExposedPropPatchMethod(String uri, List changeList) throws IOException {
            super(uri, changeList);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
