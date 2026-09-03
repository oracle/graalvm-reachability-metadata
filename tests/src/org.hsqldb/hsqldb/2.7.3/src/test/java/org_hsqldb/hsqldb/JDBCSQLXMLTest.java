/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLXML;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXResult;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class JDBCSQLXMLTest {
    private static final String XML = "<root>value</root>";

    @Test
    void readsXmlThroughSupportedSourceTypes() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:sqlxml_sources")) {
            SQLXML streamXml = createXml(connection);
            try {
                StreamSource streamSource = streamXml.getSource(StreamSource.class);
                assertThat(readAll(streamSource.getReader())).contains(XML);
            } finally {
                streamXml.free();
            }

            SQLXML domXml = createXml(connection);
            try {
                DOMSource domSource = domXml.getSource(DOMSource.class);
                assertRootValue((Document) domSource.getNode());
            } finally {
                domXml.free();
            }

            SQLXML saxXml = createXml(connection);
            try {
                SAXSource saxSource = saxXml.getSource(SAXSource.class);
                assertThat(readAll(saxSource.getInputSource().getCharacterStream())).contains(XML);
            } finally {
                saxXml.free();
            }

            SQLXML staxXml = createXml(connection);
            try {
                StAXSource staxSource = staxXml.getSource(StAXSource.class);
                assertThat(readStaxText(staxSource.getXMLEventReader())).isEqualTo("value");
            } finally {
                staxXml.free();
            }
        }
    }

    @Test
    void writesXmlThroughSupportedResultTypes() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:sqlxml_results")) {
            initializeTransformer(connection);

            SQLXML streamXml = connection.createSQLXML();
            try {
                StreamResult streamResult = streamXml.setResult(StreamResult.class);
                try (OutputStream outputStream = streamResult.getOutputStream()) {
                    outputStream.write(XML.getBytes(StandardCharsets.UTF_8));
                }
                assertStoredValue(streamXml);
            } finally {
                streamXml.free();
            }

            SQLXML domXml = connection.createSQLXML();
            try {
                DOMResult domResult = domXml.setResult(DOMResult.class);
                domResult.setNode(documentWithRootValue());
                assertStoredValue(domXml);
            } finally {
                domXml.free();
            }

            SQLXML saxXml = connection.createSQLXML();
            try {
                SAXResult saxResult = saxXml.setResult(SAXResult.class);
                writeSaxDocument(saxResult.getHandler());
                assertStoredValue(saxXml);
            } finally {
                saxXml.free();
            }

            SQLXML staxXml = connection.createSQLXML();
            try {
                CustomStAXResult staxResult = staxXml.setResult(CustomStAXResult.class);
                XMLStreamWriter writer = staxResult.getXMLStreamWriter();
                writer.writeStartDocument();
                writer.writeStartElement("root");
                writer.writeCharacters("value");
                writer.writeEndElement();
                writer.writeEndDocument();
                writer.close();
                assertStoredValue(staxXml);
            } finally {
                staxXml.free();
            }
        }
    }

    private static SQLXML createXml(Connection connection) throws SQLException {
        SQLXML sqlXml = connection.createSQLXML();
        sqlXml.setString(XML);
        return sqlXml;
    }

    private static void initializeTransformer(Connection connection) throws SQLException {
        SQLXML sqlXml = createXml(connection);
        sqlXml.free();
    }

    private static void assertStoredValue(SQLXML sqlXml) throws SQLException {
        DOMSource source = sqlXml.getSource(DOMSource.class);
        assertRootValue((Document) source.getNode());
    }

    private static void assertRootValue(Document document) {
        assertThat(document.getDocumentElement().getTagName()).isEqualTo("root");
        assertThat(document.getDocumentElement().getTextContent()).isEqualTo("value");
    }

    private static Document documentWithRootValue() throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = document.createElement("root");
        root.setTextContent("value");
        document.appendChild(root);
        return document;
    }

    private static void writeSaxDocument(ContentHandler handler) throws SAXException {
        Attributes attributes = new AttributesImpl();
        char[] text = "value".toCharArray();
        handler.startDocument();
        handler.startElement("", "root", "root", attributes);
        handler.characters(text, 0, text.length);
        handler.endElement("", "root", "root");
        handler.endDocument();
    }

    private static String readAll(Reader reader) throws IOException {
        try (Reader input = reader) {
            StringBuilder content = new StringBuilder();
            char[] buffer = new char[128];
            int count;
            while ((count = input.read(buffer)) != -1) {
                content.append(buffer, 0, count);
            }
            return content.toString();
        }
    }

    private static String readStaxText(XMLEventReader eventReader) throws XMLStreamException {
        try {
            StringBuilder content = new StringBuilder();
            while (eventReader.hasNext()) {
                XMLEvent event = eventReader.nextEvent();
                if (event.isCharacters()) {
                    content.append(event.asCharacters().getData());
                }
            }
            return content.toString();
        } finally {
            eventReader.close();
        }
    }

    public static final class CustomStAXResult extends StAXResult {
        public CustomStAXResult(XMLStreamWriter writer) {
            super(writer);
        }
    }
}
