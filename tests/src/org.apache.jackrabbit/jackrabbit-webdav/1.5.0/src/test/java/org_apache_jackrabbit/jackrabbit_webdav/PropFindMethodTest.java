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
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
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

public class PropFindMethodTest {
    @Test
    void createsPropFindRequestForNamedProperties() throws Exception {
        DavPropertyNameSet properties = new DavPropertyNameSet();
        properties.add(DavPropertyName.DISPLAYNAME);
        properties.add(DavPropertyName.GETCONTENTLENGTH);

        PropFindMethod method = new PropFindMethod(
                "/repository/default/file.txt",
                properties,
                DavConstants.DEPTH_1);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_PROPFIND);
        assertThat(method.getPath()).isEqualTo("/repository/default/file.txt");

        Header depthHeader = method.getRequestHeader(DavConstants.HEADER_DEPTH);
        assertThat(depthHeader).isNotNull();
        assertThat(depthHeader.getValue()).isEqualTo("1");

        Document requestDocument = parseRequestEntity(method.getRequestEntity());
        Element propfindElement = requestDocument.getDocumentElement();
        assertThat(DomUtil.matches(
                propfindElement,
                DavConstants.XML_PROPFIND,
                DavConstants.NAMESPACE)).isTrue();

        Element propElement = DomUtil.getChildElement(
                propfindElement,
                DavConstants.XML_PROP,
                DavConstants.NAMESPACE);
        assertThat(propElement).isNotNull();
        assertThat(DomUtil.hasChildElement(
                propElement,
                DavConstants.PROPERTY_DISPLAYNAME,
                DavConstants.NAMESPACE)).isTrue();
        assertThat(DomUtil.hasChildElement(
                propElement,
                DavConstants.PROPERTY_GETCONTENTLENGTH,
                DavConstants.NAMESPACE)).isTrue();
    }

    @Test
    void createsAllPropIncludeRequestWhenRequestedPropertiesAreSupplied() throws Exception {
        DavPropertyNameSet includedProperties = new DavPropertyNameSet();
        includedProperties.add(DavPropertyName.CREATIONDATE);

        PropFindMethod method = new PropFindMethod(
                "/repository/default/",
                DavConstants.PROPFIND_ALL_PROP_INCLUDE,
                includedProperties,
                DavConstants.DEPTH_0);

        Header depthHeader = method.getRequestHeader(DavConstants.HEADER_DEPTH);
        assertThat(depthHeader).isNotNull();
        assertThat(depthHeader.getValue()).isEqualTo("0");

        Document requestDocument = parseRequestEntity(method.getRequestEntity());
        Element propfindElement = requestDocument.getDocumentElement();
        assertThat(DomUtil.hasChildElement(
                propfindElement,
                DavConstants.XML_ALLPROP,
                DavConstants.NAMESPACE)).isTrue();

        Element includeElement = DomUtil.getChildElement(
                propfindElement,
                DavConstants.XML_INCLUDE,
                DavConstants.NAMESPACE);
        assertThat(includeElement).isNotNull();
        assertThat(DomUtil.hasChildElement(
                includeElement,
                DavConstants.PROPERTY_CREATIONDATE,
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
