/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_wagon.wagon_webdav_jackrabbit;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.jackrabbit.webdav.client.methods.XmlRequestEntity;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlRequestEntityTest {
    @Test
    public void serializesXmlDocumentIntoRepeatableTextXmlRequestEntity() throws Exception {
        Document document = createDocument();
        XmlRequestEntity entity = new XmlRequestEntity(document);

        ByteArrayOutputStream firstWrite = new ByteArrayOutputStream();
        entity.writeRequest(firstWrite);

        String requestBody = firstWrite.toString(StandardCharsets.UTF_8.name());
        assertThat(entity.isRepeatable()).isTrue();
        assertThat(entity.getContentType()).startsWith("text/xml").contains("charset=UTF-8");
        assertThat(entity.getContentLength()).isEqualTo(firstWrite.size());
        assertThat(requestBody).contains("<resource");
        assertThat(requestBody).contains("name=\"example.txt\"");
        assertThat(requestBody).contains("content</resource>");

        ByteArrayOutputStream repeatedWrite = new ByteArrayOutputStream();
        entity.writeRequest(repeatedWrite);

        assertThat(repeatedWrite.toByteArray()).containsExactly(firstWrite.toByteArray());
    }

    private static Document createDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        Element resource = document.createElement("resource");
        resource.setAttribute("name", "example.txt");
        resource.appendChild(document.createTextNode("content"));
        document.appendChild(resource);
        return document;
    }
}
