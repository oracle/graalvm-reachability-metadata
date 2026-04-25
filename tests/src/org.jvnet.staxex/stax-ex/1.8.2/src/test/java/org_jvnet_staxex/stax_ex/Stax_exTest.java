/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jvnet_staxex.stax_ex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.jvnet.staxex.Base64Data;
import org.jvnet.staxex.Base64EncoderStream;
import org.jvnet.staxex.StreamingDataHandler;
import org.jvnet.staxex.util.DOMStreamReader;
import org.jvnet.staxex.util.XMLStreamReaderToXMLStreamWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;

class Stax_exTest {

    @Test
    void base64DataSupportsBase64ViewsAndStreamingOperations(@TempDir Path tempDir) throws Exception {
        byte[] payload = new byte[] {0, 1, 2, 3, 4, 5, 6};
        String expectedBase64 = Base64.getEncoder().encodeToString(payload);

        Base64Data data = new Base64Data();
        data.set(payload, payload.length, "application/test-binary");

        assertThat(data.hasData()).isTrue();
        assertThat(data.getDataLen()).isEqualTo(payload.length);
        assertThat(data.getMimeType()).isEqualTo("application/test-binary");
        assertThat(data.length()).isEqualTo(expectedBase64.length());
        assertThat(data.toString()).isEqualTo(expectedBase64);
        assertThat(data.getExact()).containsExactly(payload);

        for (int i = 0; i < expectedBase64.length(); i++) {
            assertThat(data.charAt(i)).isEqualTo(expectedBase64.charAt(i));
        }
        assertThat(data.subSequence(1, expectedBase64.length() - 1).toString())
                .isEqualTo(expectedBase64.substring(1, expectedBase64.length() - 1));

        char[] buffer = new char[expectedBase64.length() + 2];
        Arrays.fill(buffer, '!');
        data.writeTo(buffer, 1);
        assertThat(new String(buffer, 1, expectedBase64.length())).isEqualTo(expectedBase64);

        try (InputStream inputStream = data.getInputStream()) {
            assertThat(inputStream.readAllBytes()).containsExactly(payload);
        }

        StreamingDataHandler streamingDataHandler = (StreamingDataHandler) data.getDataHandler();
        streamingDataHandler.setHrefCid("cid:payload");

        assertThat(data.getHrefCid()).isEqualTo("cid:payload");

        try (InputStream inputStream = streamingDataHandler.readOnce()) {
            assertThat(inputStream.readAllBytes()).containsExactly(payload);
        }

        Path targetFile = tempDir.resolve("payload.bin");
        streamingDataHandler.moveTo(targetFile.toFile());
        assertThat(Files.readAllBytes(targetFile)).containsExactly(payload);
    }

    @Test
    void base64DataCanWriteBase64FromDataHandlerBackedContent() throws Exception {
        byte[] payload = "stream-backed-content".getBytes(StandardCharsets.UTF_8);
        String expectedBase64 = Base64.getEncoder().encodeToString(payload);

        Base64Data data = new Base64Data();
        data.set(new DataHandler(byteArrayDataSource(payload, "text/plain")));

        assertThat(data.hasData()).isFalse();

        try (InputStream inputStream = data.getInputStream()) {
            assertThat(inputStream.readAllBytes()).containsExactly(payload);
        }

        StreamingDataHandler streamingDataHandler = (StreamingDataHandler) data.getDataHandler();
        data.setHrefCid("cid:streaming");

        assertThat(data.getHrefCid()).isEqualTo("cid:streaming");
        try (InputStream inputStream = streamingDataHandler.readOnce()) {
            assertThat(inputStream.readAllBytes()).containsExactly(payload);
        }

        StringWriter stringWriter = new StringWriter();
        XMLStreamWriter xmlStreamWriter = XMLOutputFactory.newFactory().createXMLStreamWriter(stringWriter);
        xmlStreamWriter.writeStartDocument();
        xmlStreamWriter.writeStartElement("value");
        data.writeTo(xmlStreamWriter);
        xmlStreamWriter.writeEndElement();
        xmlStreamWriter.writeEndDocument();
        xmlStreamWriter.close();

        assertThat(stringWriter.toString()).contains("<value>" + expectedBase64 + "</value>");
        assertThat(data.getDataLen()).isEqualTo(payload.length);
        assertThat(data.getExact()).containsExactly(payload);
    }

    @Test
    void base64EncoderStreamEncodesAcrossArbitraryWriteBoundaries() throws Exception {
        byte[] payload = "Base64EncoderStream boundary coverage".getBytes(StandardCharsets.UTF_8);
        String expectedBase64 = Base64.getEncoder().encodeToString(payload);

        StringWriter stringWriter = new StringWriter();
        XMLStreamWriter xmlStreamWriter = XMLOutputFactory.newFactory().createXMLStreamWriter(stringWriter);
        xmlStreamWriter.writeStartDocument();
        xmlStreamWriter.writeStartElement("value");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Base64EncoderStream encoderStream = new Base64EncoderStream(xmlStreamWriter, outputStream);
        encoderStream.write(payload, 0, 1);
        encoderStream.write(payload, 1, 2);
        encoderStream.write(payload, 3, payload.length - 3);
        encoderStream.close();

        xmlStreamWriter.writeEndElement();
        xmlStreamWriter.writeEndDocument();
        xmlStreamWriter.close();

        assertThat(stringWriter.toString()).contains("<value>" + expectedBase64 + "</value>");
    }

    @Test
    void domStreamReaderSynthesizesNamespacesAndCoalescesAdjacentTextNodes() throws Exception {
        Document document = newNamespaceAwareDocument();
        Element root = document.createElementNS("urn:root", "root:message");
        root.setAttributeNS("urn:attribute", "attr:enabled", "true");
        root.appendChild(document.createTextNode("Hello "));
        root.appendChild(document.createTextNode("world"));
        document.appendChild(root);

        DOMStreamReader reader = new DOMStreamReader(root);

        assertThat(reader.getEventType()).isEqualTo(XMLStreamConstants.START_DOCUMENT);
        assertThat(reader.next()).isEqualTo(XMLStreamConstants.START_ELEMENT);
        assertThat(reader.getLocalName()).isEqualTo("message");
        assertThat(reader.getPrefix()).isEqualTo("root");
        assertThat(reader.getNamespaceURI()).isEqualTo("urn:root");
        assertThat(reader.getAttributeCount()).isEqualTo(1);
        assertThat(reader.getAttributeLocalName(0)).isEqualTo("enabled");
        assertThat(reader.getAttributeValue("urn:attribute", "enabled")).isEqualTo("true");

        Map<String, String> namespaces = new LinkedHashMap<>();
        for (int i = 0; i < reader.getNamespaceCount(); i++) {
            namespaces.put(reader.getNamespacePrefix(i), reader.getNamespaceURI(i));
        }

        assertThat(reader.getNamespaceCount()).isEqualTo(2);
        assertThat(namespaces)
                .containsEntry("root", "urn:root")
                .containsEntry("attr", "urn:attribute");
        assertThat(reader.getNamespaceURI("root")).isEqualTo("urn:root");
        assertThat(reader.getNamespaceURI("attr")).isEqualTo("urn:attribute");

        assertThat(reader.next()).isEqualTo(XMLStreamConstants.CHARACTERS);
        assertThat(reader.getText()).isEqualTo("Hello world");
        assertThat(reader.getTextLength()).isEqualTo("Hello world".length());

        assertThat(reader.next()).isEqualTo(XMLStreamConstants.END_ELEMENT);
        assertThat(reader.next()).isEqualTo(XMLStreamConstants.END_DOCUMENT);
    }

    @Test
    void domStreamReaderExposesProcessingInstructionsAndSkipsNonTagNodesInNextTag() throws Exception {
        Document document = newNamespaceAwareDocument();
        document.appendChild(document.createComment("before-root"));
        document.appendChild(document.createProcessingInstruction("setup", "mode='safe'"));

        Element root = document.createElementNS("urn:test", "test:root");
        root.appendChild(document.createTextNode("\n  "));
        root.appendChild(document.createComment("before-child"));
        root.appendChild(document.createProcessingInstruction("inner", "step='1'"));
        root.appendChild(document.createElementNS("urn:test", "test:child"));
        root.appendChild(document.createComment("after-child"));
        root.appendChild(document.createTextNode("\n"));
        document.appendChild(root);

        DOMStreamReader reader = new DOMStreamReader(document);

        assertThat(reader.getEventType()).isEqualTo(XMLStreamConstants.START_DOCUMENT);
        assertThat(reader.next()).isEqualTo(XMLStreamConstants.COMMENT);
        assertThat(reader.next()).isEqualTo(XMLStreamConstants.PROCESSING_INSTRUCTION);
        assertThat(reader.getPITarget()).isEqualTo("setup");
        assertThat(reader.getPIData()).isEqualTo("mode='safe'");

        assertThat(reader.nextTag()).isEqualTo(XMLStreamConstants.START_ELEMENT);
        reader.require(XMLStreamConstants.START_ELEMENT, "urn:test", "root");
        assertThat(reader.getLocalName()).isEqualTo("root");

        assertThat(reader.next()).isEqualTo(XMLStreamConstants.CHARACTERS);
        assertThat(reader.isWhiteSpace()).isTrue();
        assertThat(reader.next()).isEqualTo(XMLStreamConstants.COMMENT);
        assertThat(reader.next()).isEqualTo(XMLStreamConstants.PROCESSING_INSTRUCTION);
        assertThat(reader.getPITarget()).isEqualTo("inner");
        assertThat(reader.getPIData()).isEqualTo("step='1'");

        assertThat(reader.nextTag()).isEqualTo(XMLStreamConstants.START_ELEMENT);
        reader.require(XMLStreamConstants.START_ELEMENT, "urn:test", "child");

        assertThat(reader.nextTag()).isEqualTo(XMLStreamConstants.END_ELEMENT);
        reader.require(XMLStreamConstants.END_ELEMENT, "urn:test", "child");

        assertThat(reader.nextTag()).isEqualTo(XMLStreamConstants.END_ELEMENT);
        reader.require(XMLStreamConstants.END_ELEMENT, "urn:test", "root");
        assertThat(reader.next()).isEqualTo(XMLStreamConstants.END_DOCUMENT);
    }

    @Test
    void xmlStreamReaderToXmlStreamWriterBridgesLeadingCommentsAndProcessingInstructions() throws Exception {
        String xml = "<!--before-root--><root xmlns=\"urn:test\" xmlns:ex=\"urn:extra\" ex:flag=\"yes\">"
                + "<child><![CDATA[Hello <xml> & more]]></child>"
                + "<!--inside-root-->"
                + "<?target value?>"
                + "<empty/></root>";

        XMLInputFactory inputFactory = XMLInputFactory.newFactory();
        inputFactory.setProperty(XMLInputFactory.IS_COALESCING, false);

        StringWriter output = new StringWriter();
        XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
        XMLStreamReader reader = inputFactory.createXMLStreamReader(new StringReader(xml));

        new XMLStreamReaderToXMLStreamWriter().bridge(reader, writer);
        writer.close();
        reader.close();

        String bridgedXml = output.toString();
        assertThat(bridgedXml)
                .contains("<!--before-root-->")
                .contains("<!--inside-root-->")
                .contains("<?target value?>");

        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        Document bridgedDocument = documentBuilderFactory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(bridgedXml.getBytes(StandardCharsets.UTF_8)));
        Element root = bridgedDocument.getDocumentElement();

        assertThat(root.getLocalName()).isEqualTo("root");
        assertThat(root.getNamespaceURI()).isEqualTo("urn:test");
        assertThat(root.getAttributeNS("urn:extra", "flag")).isEqualTo("yes");
        assertThat(root.getElementsByTagNameNS("urn:test", "child").item(0).getTextContent())
                .isEqualTo("Hello <xml> & more");
        assertThat(root.getElementsByTagNameNS("urn:test", "empty").getLength()).isEqualTo(1);
    }

    private static Document newNamespaceAwareDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().newDocument();
    }

    private static DataSource byteArrayDataSource(byte[] payload, String contentType) {
        return new DataSource() {
            @Override
            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(payload);
            }

            @Override
            public OutputStream getOutputStream() {
                throw new UnsupportedOperationException("Writing is not supported");
            }

            @Override
            public String getContentType() {
                return contentType;
            }

            @Override
            public String getName() {
                return "in-memory";
            }
        };
    }
}
