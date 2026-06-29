/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.XMLEvent;

import org.hsqldb.dynamicaccess.CustomDOMResult;
import org.hsqldb.dynamicaccess.CustomDOMSource;
import org.hsqldb.dynamicaccess.CustomSAXResult;
import org.hsqldb.dynamicaccess.CustomSAXSource;
import org.hsqldb.dynamicaccess.CustomStAXResult;
import org.hsqldb.dynamicaccess.CustomStAXSource;
import org.hsqldb.dynamicaccess.CustomStreamResult;
import org.hsqldb.dynamicaccess.CustomStreamSource;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.AttributesImpl;

public class JDBCSQLXMLTest {
    private static final String XML = "<document><item id=\"42\">value</item></document>";

    @Test
    void createsSupportedSourceImplementations() throws Exception {
        try (Connection connection = openConnection()) {
            assertStreamSource(connection);
            assertDOMSource(connection);
            assertSAXSource(connection);
            assertStAXSource(connection);
        }
    }

    @Test
    void createsSupportedResultImplementations() throws Exception {
        try (Connection connection = openConnection()) {
            initializeXmlTransformer(connection);
            assertStreamResult(connection);
            assertDOMResult(connection);
            assertSAXResult(connection);
            assertStAXResult(connection);
        }
    }

    private static Connection openConnection() throws SQLException {
        JDBCDataSource dataSource = new JDBCDataSource();
        String databaseName = "JDBCSQLXMLTest" + UUID.randomUUID().toString().replace("-", "");

        dataSource.setUrl("jdbc:hsqldb:mem:" + databaseName + ";shutdown=true");
        dataSource.setUser("SA");
        dataSource.setPassword("");

        return dataSource.getConnection();
    }

    private static SQLXML createReadableSQLXML(Connection connection) throws SQLException {
        SQLXML sqlxml = connection.createSQLXML();

        sqlxml.setString(XML);

        return sqlxml;
    }

    private static void initializeXmlTransformer(Connection connection) throws SQLException {
        SQLXML sqlxml = createReadableSQLXML(connection);

        sqlxml.free();
    }

    private static void assertStreamSource(Connection connection) throws Exception {
        SQLXML sqlxml = createReadableSQLXML(connection);

        try {
            CustomStreamSource source = sqlxml.getSource(CustomStreamSource.class);

            try (Reader reader = source.getReader()) {
                assertXmlPayload(readAll(reader));
            }
        } finally {
            sqlxml.free();
        }
    }

    private static void assertDOMSource(Connection connection) throws Exception {
        SQLXML sqlxml = createReadableSQLXML(connection);

        try {
            CustomDOMSource source = sqlxml.getSource(CustomDOMSource.class);
            Node node = source.getNode();

            assertThat(node).isInstanceOf(Document.class);

            Document document = (Document) node;

            assertThat(document.getDocumentElement().getNodeName()).isEqualTo("document");
            assertThat(document.getDocumentElement().getElementsByTagName("item").item(0).getTextContent())
                    .isEqualTo("value");
        } finally {
            sqlxml.free();
        }
    }

    private static void assertSAXSource(Connection connection) throws Exception {
        SQLXML sqlxml = createReadableSQLXML(connection);

        try {
            CustomSAXSource source = sqlxml.getSource(CustomSAXSource.class);

            try (Reader reader = source.getInputSource().getCharacterStream()) {
                assertXmlPayload(readAll(reader));
            }
        } finally {
            sqlxml.free();
        }
    }

    private static void assertStAXSource(Connection connection) throws Exception {
        SQLXML sqlxml = createReadableSQLXML(connection);

        try {
            CustomStAXSource source = sqlxml.getSource(CustomStAXSource.class);
            XMLEventReader reader = source.getXMLEventReader();
            boolean sawItem = false;
            boolean sawValue = false;

            try {
                while (reader.hasNext()) {
                    XMLEvent event = reader.nextEvent();

                    if (event.isStartElement()
                            && "item".equals(event.asStartElement().getName().getLocalPart())) {
                        sawItem = true;
                    }
                    if (event.isCharacters() && "value".equals(event.asCharacters().getData())) {
                        sawValue = true;
                    }
                }
            } finally {
                reader.close();
            }

            assertThat(sawItem).isTrue();
            assertThat(sawValue).isTrue();
        } finally {
            sqlxml.free();
        }
    }

    private static void assertStreamResult(Connection connection) throws Exception {
        SQLXML sqlxml = connection.createSQLXML();

        try {
            CustomStreamResult result = sqlxml.setResult(CustomStreamResult.class);

            try (OutputStream outputStream = result.getOutputStream()) {
                outputStream.write(XML.getBytes(StandardCharsets.UTF_8));
            }

            assertXmlPayload(sqlxml.getString());
        } finally {
            sqlxml.free();
        }
    }

    private static void assertDOMResult(Connection connection) throws Exception {
        SQLXML sqlxml = connection.createSQLXML();

        try {
            CustomDOMResult result = sqlxml.setResult(CustomDOMResult.class);
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new StringReader(XML)));

            result.setNode(document);

            assertXmlPayload(sqlxml.getString());
        } finally {
            sqlxml.free();
        }
    }

    private static void assertSAXResult(Connection connection) throws Exception {
        SQLXML sqlxml = connection.createSQLXML();

        try {
            CustomSAXResult result = sqlxml.setResult(CustomSAXResult.class);
            ContentHandler handler = result.getHandler();
            AttributesImpl attributes = new AttributesImpl();
            char[] value = "value".toCharArray();

            attributes.addAttribute("", "id", "id", "CDATA", "42");
            handler.startDocument();
            handler.startElement("", "document", "document", new AttributesImpl());
            handler.startElement("", "item", "item", attributes);
            handler.characters(value, 0, value.length);
            handler.endElement("", "item", "item");
            handler.endElement("", "document", "document");
            handler.endDocument();

            assertXmlPayload(sqlxml.getString());
        } finally {
            sqlxml.free();
        }
    }

    private static void assertStAXResult(Connection connection) throws Exception {
        SQLXML sqlxml = connection.createSQLXML();

        try {
            CustomStAXResult result = sqlxml.setResult(CustomStAXResult.class);
            XMLStreamWriter writer = result.getXMLStreamWriter();

            writer.writeStartDocument();
            writer.writeStartElement("document");
            writer.writeStartElement("item");
            writer.writeAttribute("id", "42");
            writer.writeCharacters("value");
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.flush();
            writer.close();

            assertXmlPayload(sqlxml.getString());
        } finally {
            sqlxml.free();
        }
    }

    private static String readAll(Reader reader) throws Exception {
        StringWriter writer = new StringWriter();

        reader.transferTo(writer);

        return writer.toString();
    }

    private static void assertXmlPayload(String xml) {
        assertThat(xml).contains("<document");
        assertThat(xml).contains("<item");
        assertThat(xml).contains("id=\"42\"");
        assertThat(xml).contains(">value<");
    }
}
