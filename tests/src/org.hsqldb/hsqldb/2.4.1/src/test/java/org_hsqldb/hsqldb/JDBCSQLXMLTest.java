/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import javax.xml.stream.XMLEventReader;
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

import org.hsqldb.jdbc.JDBCSQLXML;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.AttributesImpl;

public class JDBCSQLXMLTest {
    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root><child>value</child></root>";

    @Test
    void createsStreamSourceWithRequestedSourceClass() throws Exception {
        JDBCSQLXML sqlxml = newReadableSqlXml();

        try {
            CustomStreamSource source = sqlxml.getSource(CustomStreamSource.class);

            assertThat(source.getReader()).isNotNull();
        } finally {
            sqlxml.free();
        }
    }

    @Test
    void createsDomSourceWithRequestedSourceClass() throws Exception {
        JDBCSQLXML sqlxml = newReadableSqlXml();

        try {
            CustomDOMSource source = sqlxml.getSource(CustomDOMSource.class);

            assertThat(source.getNode()).isInstanceOf(Document.class);
        } finally {
            sqlxml.free();
        }
    }

    @Test
    void createsSaxSourceWithRequestedSourceClass() throws Exception {
        JDBCSQLXML sqlxml = newReadableSqlXml();

        try {
            CustomSAXSource source = sqlxml.getSource(CustomSAXSource.class);

            assertThat(source.getInputSource()).isNotNull();
            assertThat(source.getInputSource().getCharacterStream()).isNotNull();
        } finally {
            sqlxml.free();
        }
    }

    @Test
    void createsStaxSourceWithRequestedSourceClass() throws Exception {
        JDBCSQLXML sqlxml = newReadableSqlXml();

        try {
            CustomStAXSource source = sqlxml.getSource(CustomStAXSource.class);

            assertThat(source.getXMLEventReader()).isNotNull();
        } finally {
            sqlxml.free();
        }
    }

    @Test
    void createsStreamResultWithRequestedResultClass() throws Exception {
        withWritableSqlXml(sqlxml -> {
            CustomStreamResult result = sqlxml.setResult(CustomStreamResult.class);

            try (OutputStream outputStream = result.getOutputStream()) {
                outputStream.write(XML.getBytes(StandardCharsets.UTF_8));
            }

            assertThat(result.getOutputStream()).isNotNull();
        });
    }

    @Test
    void createsDomResultWithRequestedResultClass() throws Exception {
        withWritableSqlXml(sqlxml -> {
            CustomDOMResult result = sqlxml.setResult(CustomDOMResult.class);

            assertThat(result).isNotNull();
        });
    }

    @Test
    void createsSaxResultWithRequestedResultClass() throws Exception {
        withWritableSqlXml(sqlxml -> {
            CustomSAXResult result = sqlxml.setResult(CustomSAXResult.class);
            ContentHandler handler = result.getHandler();

            handler.startDocument();
            handler.startElement("", "root", "root", new AttributesImpl());
            handler.characters("value".toCharArray(), 0, "value".length());
            handler.endElement("", "root", "root");
            handler.endDocument();

            assertThat(handler).isNotNull();
        });
    }

    @Test
    void createsStaxResultWithRequestedResultClass() throws Exception {
        withWritableSqlXml(sqlxml -> {
            CustomStAXResult result = sqlxml.setResult(CustomStAXResult.class);
            XMLStreamWriter writer = result.getXMLStreamWriter();

            writer.writeStartDocument();
            writer.writeStartElement("root");
            writer.writeCharacters("value");
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.close();

            assertThat(writer).isNotNull();
        });
    }

    private static JDBCSQLXML newReadableSqlXml() throws Exception {
        return new JDBCSQLXML(new StreamSource(new StringReader(XML)));
    }

    private static void withWritableSqlXml(SqlXmlConsumer consumer) throws Exception {
        JDBCSQLXML sqlxml = new WritableJDBCSQLXML();

        try {
            consumer.accept(sqlxml);
        } finally {
            sqlxml.free();
        }
    }

    @FunctionalInterface
    private interface SqlXmlConsumer {
        void accept(JDBCSQLXML sqlxml) throws Exception;
    }

    public static final class WritableJDBCSQLXML extends JDBCSQLXML {
    }

    public static final class CustomStreamSource extends StreamSource {
    }

    public static final class CustomDOMSource extends DOMSource {
    }

    public static final class CustomSAXSource extends SAXSource {
    }

    public static final class CustomStAXSource extends StAXSource {
        public CustomStAXSource(XMLEventReader reader) throws XMLStreamException {
            super(reader);
        }
    }

    public static final class CustomStreamResult extends StreamResult {
    }

    public static final class CustomDOMResult extends DOMResult {
    }

    public static final class CustomSAXResult extends SAXResult {
    }

    public static final class CustomStAXResult extends StAXResult {
        public CustomStAXResult(XMLStreamWriter writer) {
            super(writer);
        }
    }
}
