/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_xml_stream.stax_api;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.Comment;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.ProcessingInstruction;
import javax.xml.stream.events.StartElement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
public class XMLEventFactoryTest {
    @Test
    void assemblesNamespacedEventsIntoAReadableDocument() throws Exception {
        XMLEventFactory factory = XMLEventFactory.newInstance();
        QName elementName = new QName("urn:notes", "note", "n");
        Attribute attribute = factory.createAttribute("id", "draft");
        Namespace namespace = factory.createNamespace("n", "urn:notes");
        StartElement start = factory.createStartElement(elementName,
                List.of(attribute).iterator(), List.of(namespace).iterator());
        assertEquals(elementName, start.getName());
        assertEquals("draft", start.getAttributeByName(new QName("id")).getValue());
        assertEquals("urn:notes", start.getNamespaceURI("n"));

        StringWriter output = new StringWriter();
        XMLEventWriter writer = XMLOutputFactory.newInstance().createXMLEventWriter(output);
        try {
            writer.add(factory.createStartDocument());
            writer.add(start);
            writer.add(factory.createCharacters("Read <this> & that"));
            writer.add(factory.createEndElement(elementName, List.of(namespace).iterator()));
            writer.add(factory.createEndDocument());
            writer.flush();
        } finally {
            writer.close();
        }

        XMLStreamReader reader = XMLInputFactory.newInstance()
                .createXMLStreamReader(new StringReader(output.toString()));
        try {
            assertEquals(XMLStreamConstants.START_ELEMENT, reader.nextTag());
            assertEquals(elementName, reader.getName());
            assertEquals("draft", reader.getAttributeValue(null, "id"));
            assertEquals("Read <this> & that", reader.getElementText());
            assertEquals(XMLStreamConstants.END_DOCUMENT, reader.next());
        } finally {
            reader.close();
        }
    }

    @Test
    void createsAndEncodesCharacterCommentAndProcessingInstructionEvents() throws Exception {
        XMLEventFactory factory = XMLEventFactory.newInstance();
        Characters text = factory.createCharacters("<note>&");
        StringWriter encoded = new StringWriter();
        text.writeAsEncodedUnicode(encoded);
        assertEquals("&lt;note&gt;&amp;", encoded.toString());

        Characters cdata = factory.createCData("raw <markup>");
        assertTrue(cdata.isCData());
        assertEquals("raw <markup>", cdata.getData());
        assertTrue(factory.createSpace(" \n").isWhiteSpace());
        assertTrue(factory.createIgnorableSpace(" ").isIgnorableWhiteSpace());

        Comment comment = factory.createComment("reviewed");
        assertEquals(XMLStreamConstants.COMMENT, comment.getEventType());
        assertEquals("reviewed", comment.getText());
        ProcessingInstruction instruction = factory.createProcessingInstruction("render", "mode=\"compact\"");
        assertEquals(XMLStreamConstants.PROCESSING_INSTRUCTION, instruction.getEventType());
        assertEquals("render", instruction.getTarget());
        assertEquals("mode=\"compact\"", instruction.getData());
    }
}
