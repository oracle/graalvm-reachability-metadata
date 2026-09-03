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
import org.apache.jackrabbit.webdav.client.methods.PropPatchMethod;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class PropPatchMethodTest {
    @Test
    void createsPropPatchRequestFromOrderedChangeList() throws Exception {
        List<Object> changes = Arrays.asList(
                new DefaultDavProperty(DavPropertyName.DISPLAYNAME, "updated title"),
                DavPropertyName.GETCONTENTLANGUAGE,
                new DefaultDavProperty(DavPropertyName.GETCONTENTTYPE, "text/plain"));

        PropPatchMethod method = new PropPatchMethod("/repository/default/file.txt", changes);

        assertThat(method.getName()).isEqualTo(DavMethods.METHOD_PROPPATCH);
        assertThat(method.getPath()).isEqualTo("/repository/default/file.txt");

        Document requestDocument = parseRequestEntity(method.getRequestEntity());
        Element propertyUpdateElement = requestDocument.getDocumentElement();
        assertThat(DomUtil.matches(
                propertyUpdateElement,
                DavConstants.XML_PROPERTYUPDATE,
                DavConstants.NAMESPACE)).isTrue();

        Element setElement = DomUtil.getChildElement(
                propertyUpdateElement,
                DavConstants.XML_SET,
                DavConstants.NAMESPACE);
        assertThat(setElement).isNotNull();
        Element setPropElement = DomUtil.getChildElement(
                setElement,
                DavConstants.XML_PROP,
                DavConstants.NAMESPACE);
        assertThat(setPropElement).isNotNull();
        assertThat(DomUtil.getChildText(
                setPropElement,
                DavConstants.PROPERTY_DISPLAYNAME,
                DavConstants.NAMESPACE)).isEqualTo("updated title");

        Element removeElement = DomUtil.getChildElement(
                propertyUpdateElement,
                DavConstants.XML_REMOVE,
                DavConstants.NAMESPACE);
        assertThat(removeElement).isNotNull();
        Element removePropElement = DomUtil.getChildElement(
                removeElement,
                DavConstants.XML_PROP,
                DavConstants.NAMESPACE);
        assertThat(removePropElement).isNotNull();
        assertThat(DomUtil.hasChildElement(
                removePropElement,
                DavConstants.PROPERTY_GETCONTENTLANGUAGE,
                DavConstants.NAMESPACE)).isTrue();
    }

    @Test
    void createsPropPatchRequestFromSetAndRemoveContainers() throws Exception {
        DavPropertySet setProperties = new DavPropertySet();
        setProperties.add(new DefaultDavProperty(DavPropertyName.DISPLAYNAME, "renamed resource"));

        DavPropertyNameSet removeProperties = new DavPropertyNameSet();
        removeProperties.add(DavPropertyName.GETCONTENTLENGTH);

        PropPatchMethod method = new PropPatchMethod(
                "/repository/default/file.txt",
                setProperties,
                removeProperties);

        Document requestDocument = parseRequestEntity(method.getRequestEntity());
        Element propertyUpdateElement = requestDocument.getDocumentElement();

        Element setPropElement = DomUtil.getChildElement(
                DomUtil.getChildElement(
                        propertyUpdateElement,
                        DavConstants.XML_SET,
                        DavConstants.NAMESPACE),
                DavConstants.XML_PROP,
                DavConstants.NAMESPACE);
        assertThat(setPropElement).isNotNull();
        assertThat(DomUtil.getChildText(
                setPropElement,
                DavConstants.PROPERTY_DISPLAYNAME,
                DavConstants.NAMESPACE)).isEqualTo("renamed resource");

        Element removePropElement = DomUtil.getChildElement(
                DomUtil.getChildElement(
                        propertyUpdateElement,
                        DavConstants.XML_REMOVE,
                        DavConstants.NAMESPACE),
                DavConstants.XML_PROP,
                DavConstants.NAMESPACE);
        assertThat(removePropElement).isNotNull();
        assertThat(DomUtil.hasChildElement(
                removePropElement,
                DavConstants.PROPERTY_GETCONTENTLENGTH,
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
