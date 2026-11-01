/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_wagon.wagon_webdav_jackrabbit;

import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.apache.jackrabbit.webdav.client.methods.XmlRequestEntity;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XmlRequestEntityTest {
    @Test
    void serializesRepeatableUtf8WebDavRequest() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        Element update = document.createElementNS("DAV:", "d:propertyupdate");
        document.appendChild(update);
        Element set = document.createElementNS("DAV:", "d:set");
        update.appendChild(set);
        Element properties = document.createElementNS("DAV:", "d:prop");
        set.appendChild(properties);
        Element displayName = document.createElementNS("DAV:", "d:displayname");
        String name = "Caf\u00e9 & <draft>";
        displayName.setTextContent(name);
        properties.appendChild(displayName);

        XmlRequestEntity entity = new XmlRequestEntity(document);
        assertTrue(entity.isRepeatable());
        assertEquals("text/xml; charset=UTF-8", entity.getContentType());

        ByteArrayOutputStream first = new ByteArrayOutputStream();
        entity.writeRequest(first);
        byte[] bytes = first.toByteArray();
        assertEquals(bytes.length, entity.getContentLength());
        String xml = first.toString(StandardCharsets.UTF_8);
        assertTrue(xml.contains("Caf\u00e9 &amp; &lt;draft&gt;"));

        Document parsed = builder.parse(new ByteArrayInputStream(bytes));
        assertEquals("DAV:", parsed.getDocumentElement().getNamespaceURI());
        assertEquals("propertyupdate", parsed.getDocumentElement().getLocalName());
        assertEquals(1, parsed.getElementsByTagNameNS("DAV:", "displayname").getLength());
        assertEquals(name, parsed.getElementsByTagNameNS("DAV:", "displayname").item(0).getTextContent());

        ByteArrayOutputStream second = new ByteArrayOutputStream();
        entity.writeRequest(second);
        assertArrayEquals(bytes, second.toByteArray());
    }

    /// Builds the PROPFIND request `WebDavWagon` sends. Reaching `DavMethodBase` also activates
    /// the jackrabbit-webdav metadata conditioned on it; while that condition is unreached,
    /// Native Image 25.0 fails with a `TypeNotFoundError` for commons-logging's `Log4JLogger`
    /// (GR-80931, https://github.com/oracle/graal/issues/14805).
    @Test
    void buildsPropFindRequestLikeWebDavWagon() throws Exception {
        DavPropertyNameSet names = new DavPropertyNameSet();
        names.add(DavPropertyName.create(DavConstants.PROPERTY_RESOURCETYPE));
        PropFindMethod method = new PropFindMethod("http://localhost/repository/", names, DavConstants.DEPTH_1);
        assertEquals("PROPFIND", method.getName());

        RequestEntity entity = method.getRequestEntity();
        assertInstanceOf(XmlRequestEntity.class, entity);
        assertEquals("text/xml; charset=UTF-8", entity.getContentType());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        entity.writeRequest(out);
        String xml = out.toString(StandardCharsets.UTF_8);
        assertTrue(xml.contains("propfind"));
        assertTrue(xml.contains("resourcetype"));
        assertEquals("1", method.getRequestHeader("Depth").getValue());
    }
}
