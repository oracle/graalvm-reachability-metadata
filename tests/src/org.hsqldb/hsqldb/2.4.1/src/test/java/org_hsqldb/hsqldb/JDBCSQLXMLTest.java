/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXResult;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.hsqldb.jdbc.JDBCSQLXML;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.AttributesImpl;

public class JDBCSQLXMLTest {
    private static final String XML = "<document><message>Hello SQLXML</message></document>";

    @Test
    void getsStreamSourceThroughPublicSqlXmlApi() throws Exception {
        JDBCSQLXML sqlXml = readableSqlXml();

        StreamSource source = sqlXml.getSource(StreamSource.class);

        try (Reader reader = source.getReader()) {
            assertThat(readAll(reader)).contains("Hello SQLXML");
        } finally {
            sqlXml.free();
        }
    }

    @Test
    void getsDomSourceThroughPublicSqlXmlApi() throws Exception {
        JDBCSQLXML sqlXml = readableSqlXml();

        DOMSource source = sqlXml.getSource(DOMSource.class);

        try {
            Document document = (Document) source.getNode();
            assertThat(document.getDocumentElement().getNodeName()).isEqualTo("document");
            assertThat(document.getDocumentElement().getTextContent())
                    .isEqualTo("Hello SQLXML");
        } finally {
            sqlXml.free();
        }
    }

    @Test
    void getsSaxSourceThroughPublicSqlXmlApi() throws Exception {
        JDBCSQLXML sqlXml = readableSqlXml();

        SAXSource source = sqlXml.getSource(SAXSource.class);

        try {
            InputSource inputSource = source.getInputSource();
            try (Reader reader = inputSource.getCharacterStream()) {
                assertThat(readAll(reader)).contains("<message>Hello SQLXML</message>");
            }
        } finally {
            sqlXml.free();
        }
    }

    @Test
    void getsStaxSourceThroughPublicSqlXmlApi() throws Exception {
        JDBCSQLXML sqlXml = readableSqlXml();

        StAXSource source = sqlXml.getSource(StAXSource.class);

        try {
            XMLEventReader reader = source.getXMLEventReader();
            try {
                assertThat(reader.nextEvent().isStartDocument()).isTrue();
                assertThat(reader.nextTag().asStartElement().getName().getLocalPart())
                        .isEqualTo("document");
            } finally {
                reader.close();
            }
        } finally {
            sqlXml.free();
        }
    }

    @Test
    void setsStreamResultThroughPublicSqlXmlApi() throws Exception {
        JDBCSQLXML sqlXml = writableSqlXml();

        StreamResult result = sqlXml.setResult(StreamResult.class);

        try {
            result.getOutputStream().write(XML.getBytes(StandardCharsets.UTF_8));
            result.getOutputStream().close();
            assertThat(result.getOutputStream()).isNotNull();
        } finally {
            sqlXml.free();
        }
    }

    @Test
    void setsDomResultThroughPublicSqlXmlApi() throws Exception {
        JDBCSQLXML sqlXml = writableSqlXml();

        DOMResult result = sqlXml.setResult(DOMResult.class);

        try {
            assertThat(result).isNotNull();
        } finally {
            sqlXml.free();
        }
    }

    @Test
    void setsSaxResultThroughPublicSqlXmlApi() throws Exception {
        JDBCSQLXML sqlXml = writableSqlXml();

        SAXResult result = sqlXml.setResult(SAXResult.class);

        try {
            ContentHandler handler = result.getHandler();
            handler.startDocument();
            handler.startElement("", "document", "document", new AttributesImpl());
            handler.characters("Hello SQLXML".toCharArray(), 0, "Hello SQLXML".length());
            handler.endElement("", "document", "document");
            handler.endDocument();
            assertThat(result.getHandler()).isSameAs(handler);
        } finally {
            sqlXml.free();
        }
    }

    @Test
    void setsCustomStaxResultThroughPublicSqlXmlApi() throws Exception {
        JDBCSQLXML sqlXml = writableSqlXml();

        CustomStAXResult result = sqlXml.setResult(CustomStAXResult.class);

        try {
            XMLStreamWriter writer = result.getXMLStreamWriter();
            writer.writeStartDocument();
            writer.writeStartElement("document");
            writer.writeStartElement("message");
            writer.writeCharacters("Hello SQLXML");
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.flush();
            writer.close();
            assertThat(result.getXMLStreamWriter()).isSameAs(writer);
        } finally {
            sqlXml.free();
        }
    }

    private static JDBCSQLXML readableSqlXml() throws SQLException {
        WritableJDBCSQLXML sqlXml = writableSqlXml();
        sqlXml.setString(XML);

        return sqlXml;
    }

    private static WritableJDBCSQLXML writableSqlXml() {
        return new WritableJDBCSQLXML();
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[256];
        int read;

        while ((read = reader.read(buffer)) != -1) {
            builder.append(buffer, 0, read);
        }

        return builder.toString();
    }

    public static class CustomStAXResult extends StAXResult {
        public CustomStAXResult(XMLStreamWriter xmlStreamWriter) {
            super(xmlStreamWriter);
        }
    }
}

class WritableJDBCSQLXML extends JDBCSQLXML {
    WritableJDBCSQLXML() {
        super();
    }
}
