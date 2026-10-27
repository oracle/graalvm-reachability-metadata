/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_stream_buffer.streambuffer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLOutputFactory;

import org.xml.sax.XMLReader;

import com.sun.xml.stream.buffer.MutableXMLStreamBuffer;
import com.sun.xml.stream.buffer.XMLStreamBuffer;
import com.sun.xml.stream.buffer.stax.StreamReaderBufferProcessor;
import org.jvnet.staxex.Base64Data;
import org.jvnet.staxex.XMLStreamReaderEx;
import org.jvnet.staxex.XMLStreamWriterEx;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StreambufferTest {

    @Test
    void buffersStaxInfosetWithNamespacesAndMarkup() throws Exception {
        String xml = "<?xml version=\"1.0\"?><!--before--><root xmlns=\"urn:root\" "
                + "xmlns:p=\"urn:parts\" p:flag=\"yes\"><p:item><![CDATA[alpha < beta]]></p:item>"
                + "<?step done?></root>";
        XMLStreamReader source = XMLInputFactory.newFactory().createXMLStreamReader(new StringReader(xml));
        XMLStreamBuffer buffer = XMLStreamBuffer.createNewBufferFromXMLStreamReader(source);
        source.close();

        assertThat(buffer.isCreated()).isTrue();
        assertThat(buffer.isFragment()).isFalse();

        XMLStreamReader bufferedReader = buffer.readAsXMLStreamReader();
        List<String> elementNames = new ArrayList<>();
        List<String> processingInstructions = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        while (true) {
            if (bufferedReader.getEventType() == XMLStreamConstants.START_ELEMENT) {
                elementNames.add(bufferedReader.getLocalName());
                if ("root".equals(bufferedReader.getLocalName())) {
                    assertThat(bufferedReader.getNamespaceURI()).isEqualTo("urn:root");
                    assertThat(bufferedReader.getAttributeValue("urn:parts", "flag")).isEqualTo("yes");
                }
            } else if (bufferedReader.getEventType() == XMLStreamConstants.CHARACTERS
                    || bufferedReader.getEventType() == XMLStreamConstants.CDATA) {
                text.append(bufferedReader.getText());
            } else if (bufferedReader.getEventType() == XMLStreamConstants.PROCESSING_INSTRUCTION) {
                processingInstructions.add(bufferedReader.getPITarget() + " " + bufferedReader.getPIData());
            }
            if (!bufferedReader.hasNext()) {
                break;
            }
            bufferedReader.next();
        }
        bufferedReader.close();

        assertThat(elementNames).containsExactly("root", "item");
        assertThat(text).hasToString("alpha < beta");
        assertThat(processingInstructions).containsExactly("step done");

        StringWriter serialized = new StringWriter();
        XMLStreamWriter output = XMLOutputFactory.newFactory().createXMLStreamWriter(serialized);
        buffer.writeToXMLStreamWriter(output);
        output.close();
        assertThat(serialized.toString()).contains("<!--before-->", "<p:item>", "alpha &lt; beta", "<?step done?>");
    }

    @Test
    void preservesBinaryContentWrittenThroughExtendedStreamWriter() throws Exception {
        byte[] payload = "binary streambuffer payload".getBytes(StandardCharsets.UTF_8);
        MutableXMLStreamBuffer buffer = new MutableXMLStreamBuffer();
        XMLStreamWriter writer = buffer.createFromXMLStreamWriter();
        XMLStreamWriterEx extendedWriter = (XMLStreamWriterEx) writer;
        extendedWriter.writeStartDocument();
        extendedWriter.writeStartElement("attachment");
        extendedWriter.writeAttribute("contentType", "application/octet-stream");
        extendedWriter.writeBinary(new DataHandler(dataSource(payload)));
        extendedWriter.writeEndElement();
        extendedWriter.writeEndDocument();
        extendedWriter.close();

        XMLStreamReaderEx bufferedReader = (XMLStreamReaderEx) buffer.readAsXMLStreamReader();
        Base64Data binaryData = null;
        while (true) {
            if (bufferedReader.getEventType() == XMLStreamConstants.CHARACTERS
                    && bufferedReader.getPCDATA() instanceof Base64Data) {
                binaryData = (Base64Data) bufferedReader.getPCDATA();
                break;
            }
            if (!bufferedReader.hasNext()) {
                break;
            }
            bufferedReader.next();
        }
        bufferedReader.close();

        assertThat(binaryData).isNotNull();
        assertThat(binaryData.getExact()).containsExactly(payload);
        assertThat(binaryData.getMimeType()).isEqualTo("application/octet-stream");

        StringWriter serialized = new StringWriter();
        XMLStreamWriter output = XMLOutputFactory.newFactory().createXMLStreamWriter(serialized);
        buffer.writeToXMLStreamWriter(output);
        output.close();
        assertThat(serialized.toString())
                .contains(Base64.getEncoder().encodeToString(payload));
    }

    @Test
    void createsBufferFromSaxAndReadsItThroughStax() throws Exception {
        String xml = "<catalog xmlns=\"urn:catalog\"><entry id=\"first\">one</entry>"
                + "<entry id=\"second\">two</entry></catalog>";
        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        parserFactory.setNamespaceAware(true);
        XMLReader source = parserFactory.newSAXParser().getXMLReader();
        XMLStreamBuffer buffer;
        try (ByteArrayInputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            buffer = XMLStreamBuffer.createNewBufferFromXMLReader(source, input);
        }

        XMLStreamReader bufferedReader = buffer.readAsXMLStreamReader();
        List<String> elementNames = new ArrayList<>();
        List<String> entryIds = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        while (true) {
            if (bufferedReader.getEventType() == XMLStreamConstants.START_ELEMENT) {
                elementNames.add(bufferedReader.getLocalName());
                if ("entry".equals(bufferedReader.getLocalName())) {
                    entryIds.add(bufferedReader.getAttributeValue(null, "id"));
                }
            } else if (bufferedReader.getEventType() == XMLStreamConstants.CHARACTERS) {
                text.append(bufferedReader.getText());
            }
            if (!bufferedReader.hasNext()) {
                break;
            }
            bufferedReader.next();
        }
        bufferedReader.close();

        assertThat(elementNames).containsExactly("catalog", "entry", "entry");
        assertThat(entryIds).containsExactly("first", "second");
        assertThat(text).hasToString("onetwo");
    }

    @Test
    void extractsAnElementSubtreeWithAStreamReaderMark() throws Exception {
        String xml = "<catalog xmlns=\"urn:catalog\"><entry id=\"first\">one</entry>"
                + "<entry id=\"second\">two</entry></catalog>";
        XMLStreamReader source = XMLInputFactory.newFactory().createXMLStreamReader(new StringReader(xml));
        XMLStreamBuffer buffer = XMLStreamBuffer.createNewBufferFromXMLStreamReader(source);
        source.close();

        StreamReaderBufferProcessor reader = buffer.readAsXMLStreamReader();
        XMLStreamBuffer catalogMark = reader.nextTagAndMark();
        assertThat(catalogMark.isElementFragment()).isTrue();
        XMLStreamBuffer entryMark = reader.nextTagAndMark();
        reader.close();

        assertThat(entryMark.isFragment()).isTrue();
        assertThat(entryMark.isElementFragment()).isTrue();
        assertThat(entryMark.getInscopeNamespaces()).containsValue("urn:catalog");

        XMLStreamReader entryReader = entryMark.readAsXMLStreamReader();
        assertThat(entryReader.next()).isEqualTo(XMLStreamConstants.START_ELEMENT);
        assertThat(entryReader.getNamespaceURI()).isEqualTo("urn:catalog");
        assertThat(entryReader.getLocalName()).isEqualTo("entry");
        assertThat(entryReader.getAttributeValue(null, "id")).isEqualTo("first");
        assertThat(entryReader.next()).isEqualTo(XMLStreamConstants.CHARACTERS);
        assertThat(entryReader.getText()).isEqualTo("one");
        assertThat(entryReader.next()).isEqualTo(XMLStreamConstants.END_ELEMENT);
        assertThat(entryReader.next()).isEqualTo(XMLStreamConstants.END_DOCUMENT);
        entryReader.close();
    }

    @Test
    void supportsMutableBufferReuseAfterStaxRoundTrip() throws Exception {
        MutableXMLStreamBuffer buffer = new MutableXMLStreamBuffer();
        XMLStreamReader source = XMLInputFactory.newFactory()
                .createXMLStreamReader(new StringReader("<initial><value>before reset</value></initial>"));
        buffer.createFromXMLStreamReader(source);
        source.close();

        XMLStreamReader initialReader = buffer.readAsXMLStreamReader();
        while (initialReader.getEventType() != XMLStreamConstants.START_ELEMENT) {
            initialReader.next();
        }
        assertThat(initialReader.getLocalName()).isEqualTo("initial");
        initialReader.close();

        buffer.reset();
        XMLStreamWriter writer = buffer.createFromXMLStreamWriter();
        writer.writeStartDocument();
        writer.writeStartElement("replacement");
        writer.writeCharacters("after reset");
        writer.writeEndElement();
        writer.writeEndDocument();
        writer.close();

        XMLStreamReader reusedReader = buffer.readAsXMLStreamReader();
        while (reusedReader.getEventType() != XMLStreamConstants.START_ELEMENT) {
            reusedReader.next();
        }
        assertThat(reusedReader.getLocalName()).isEqualTo("replacement");
        assertThat(reusedReader.next()).isEqualTo(XMLStreamConstants.CHARACTERS);
        assertThat(reusedReader.getText()).isEqualTo("after reset");
        reusedReader.close();
    }

    private static DataSource dataSource(byte[] payload) {
        return new DataSource() {
            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(payload);
            }

            @Override
            public OutputStream getOutputStream() throws IOException {
                throw new UnsupportedOperationException("read-only data source");
            }

            @Override
            public String getContentType() {
                return "application/octet-stream";
            }

            @Override
            public String getName() {
                return "payload";
            }
        };
    }
}
