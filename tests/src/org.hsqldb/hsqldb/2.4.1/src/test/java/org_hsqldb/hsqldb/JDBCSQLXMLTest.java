/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLXML;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.XMLEvent;
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
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.AttributesImpl;

public class JDBCSQLXMLTest {
    private static final AtomicInteger DATABASE_COUNTER = new AtomicInteger();
    private static final String XML = "<root><child attribute=\"value\">text</child></root>";

    @Test
    public void createsSourceImplementationsThroughSqlXmlApi() throws Exception {
        try (Connection connection = openConnection()) {
            StreamSource streamSource = newReadableSqlXml(connection).getSource(StreamSource.class);
            assertTrue(readAll(streamSource.getReader()).contains("<child attribute=\"value\">text</child>"));

            DOMSource domSource = newReadableSqlXml(connection).getSource(DOMSource.class);
            assertEquals("root", documentFrom(domSource.getNode()).getDocumentElement().getNodeName());

            SAXSource saxSource = newReadableSqlXml(connection).getSource(SAXSource.class);
            assertTrue(readAll(saxSource.getInputSource().getCharacterStream()).contains("<root>"));

            StAXSource staxSource = newReadableSqlXml(connection).getSource(StAXSource.class);
            assertEquals("root", firstStartElementName(staxSource));
        }
    }

    @Test
    public void createsResultImplementationsThroughSqlXmlApi() throws Exception {
        try (Connection connection = openConnection()) {
            StreamResult streamResult = newWritableSqlXml(connection).setResult(StreamResult.class);
            assertNotNull(streamResult.getOutputStream());
            streamResult.getOutputStream().close();

            DOMResult domResult = newWritableSqlXml(connection).setResult(DOMResult.class);
            domResult.setNode(newDocumentWithRoot());
            assertEquals("root", documentFrom(domResult.getNode()).getDocumentElement().getNodeName());

            SAXResult saxResult = newWritableSqlXml(connection).setResult(SAXResult.class);
            writeSaxDocument(saxResult.getHandler());
            assertNotNull(saxResult.getHandler());

            ExtendingStAXResult staxResult = newWritableSqlXml(connection).setResult(ExtendingStAXResult.class);
            writeStaxDocument(staxResult.getXMLStreamWriter());
            assertNotNull(staxResult.getXMLStreamWriter());
        }
    }

    private static Connection openConnection() throws Exception {
        String databaseName = "sqlxml" + DATABASE_COUNTER.incrementAndGet();
        return DriverManager.getConnection("jdbc:hsqldb:mem:" + databaseName, "SA", "");
    }

    private static SQLXML newReadableSqlXml(Connection connection) throws Exception {
        SQLXML sqlXml = newWritableSqlXml(connection);
        sqlXml.setString(XML);
        return sqlXml;
    }

    private static SQLXML newWritableSqlXml(Connection connection) throws Exception {
        return connection.createSQLXML();
    }

    private static String readAll(Reader reader) throws IOException {
        assertNotNull(reader);
        StringWriter writer = new StringWriter();
        try (Reader openReader = reader) {
            openReader.transferTo(writer);
        }
        return writer.toString();
    }

    private static Document documentFrom(Node node) {
        assertNotNull(node);
        if (node instanceof Document) {
            return (Document) node;
        }
        return node.getOwnerDocument();
    }

    private static String firstStartElementName(StAXSource staxSource) throws Exception {
        while (staxSource.getXMLEventReader().hasNext()) {
            XMLEvent event = staxSource.getXMLEventReader().nextEvent();
            if (event.isStartElement()) {
                return event.asStartElement().getName().getLocalPart();
            }
        }
        throw new AssertionError("Expected a start element in the SQLXML StAX source");
    }

    private static Document newDocumentWithRoot() throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        document.appendChild(document.createElement("root"));
        return document;
    }

    private static void writeSaxDocument(ContentHandler handler) throws Exception {
        AttributesImpl attributes = new AttributesImpl();
        handler.startDocument();
        handler.startElement("", "root", "root", attributes);
        handler.characters("text".toCharArray(), 0, "text".length());
        handler.endElement("", "root", "root");
        handler.endDocument();
    }

    private static void writeStaxDocument(XMLStreamWriter writer) throws Exception {
        writer.writeStartDocument();
        writer.writeStartElement("root");
        writer.writeCharacters("text");
        writer.writeEndElement();
        writer.writeEndDocument();
        writer.close();
    }

    public static final class ExtendingStAXResult extends StAXResult {
        public ExtendingStAXResult(XMLStreamWriter writer) {
            super(writer);
        }
    }
}
