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

import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class PropFindMethodTest {
    @Test
    public void constructorCreatesAllPropRequest() throws Exception {
        ExposedPropFindMethod method = new ExposedPropFindMethod(
                "http://localhost:8080/repository/default",
                DavConstants.PROPFIND_ALL_PROP,
                new DavPropertyNameSet(),
                DavConstants.DEPTH_0);

        Document requestBody = requestBody(method);
        Element propfind = requestBody.getDocumentElement();
        Element child = firstElementChild(propfind);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_PROPFIND);
        assertThat(method.getPath()).isEqualTo("/repository/default");
        assertThat(method.getRequestHeader("Depth").getValue()).isEqualTo("0");
        assertThat(method.isSuccessfulStatus(DavServletResponse.SC_MULTI_STATUS)).isTrue();
        assertThat(method.isSuccessfulStatus(200)).isFalse();
        assertThat(propfind.getLocalName()).isEqualTo("propfind");
        assertThat(propfind.getNamespaceURI()).isEqualTo("DAV:");
        assertThat(child.getLocalName()).isEqualTo("allprop");
        assertThat(nextElementSibling(child)).isNull();
    }

    @Test
    public void propertyNameConstructorCreatesPropRequest() throws Exception {
        DavPropertyNameSet names = new DavPropertyNameSet();
        names.add(DavPropertyName.DISPLAYNAME);
        names.add(DavPropertyName.GETCONTENTTYPE);

        ExposedPropFindMethod method = new ExposedPropFindMethod(
                "http://localhost:8080/repository/default/file.txt",
                names,
                DavConstants.DEPTH_1);

        Document requestBody = requestBody(method);
        Element propfind = requestBody.getDocumentElement();
        Element prop = firstElementChild(propfind);

        assertThat(method.getRequestHeader("Depth").getValue()).isEqualTo("1");
        assertThat(prop.getLocalName()).isEqualTo("prop");
        assertThat(prop.getElementsByTagNameNS("DAV:", "displayname").getLength()).isEqualTo(1);
        assertThat(prop.getElementsByTagNameNS("DAV:", "getcontenttype").getLength()).isEqualTo(1);
    }

    @Test
    public void allPropIncludeCopiesRequestedNamesIntoIncludeElement() throws Exception {
        DavPropertyNameSet names = new DavPropertyNameSet();
        names.add(DavPropertyName.GETETAG);

        ExposedPropFindMethod method = new ExposedPropFindMethod(
                "http://localhost:8080/repository/default/file.txt",
                DavConstants.PROPFIND_ALL_PROP_INCLUDE,
                names,
                DavConstants.DEPTH_INFINITY);

        Document requestBody = requestBody(method);
        Element allprop = firstElementChild(requestBody.getDocumentElement());
        Element include = nextElementSibling(allprop);

        assertThat(method.getRequestHeader("Depth").getValue()).isEqualTo("infinity");
        assertThat(allprop.getLocalName()).isEqualTo("allprop");
        assertThat(include.getLocalName()).isEqualTo("include");
        assertThat(include.getElementsByTagNameNS("DAV:", "getetag").getLength()).isEqualTo(1);
    }

    private static Document requestBody(PropFindMethod method) throws Exception {
        RequestEntity requestEntity = method.getRequestEntity();
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

    private static final class ExposedPropFindMethod extends PropFindMethod {
        private ExposedPropFindMethod(String uri, int propfindType, DavPropertyNameSet propNameSet, int depth)
                throws IOException {
            super(uri, propfindType, propNameSet, depth);
        }

        private ExposedPropFindMethod(String uri, DavPropertyNameSet propNameSet, int depth) throws IOException {
            super(uri, propNameSet, depth);
        }

        private boolean isSuccessfulStatus(int statusCode) {
            return isSuccess(statusCode);
        }
    }
}
