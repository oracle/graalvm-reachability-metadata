/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_xml_stream.stax_api;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
public class XMLInputFactoryTest {
    @Test
    void readsNamespacesAttributesAndCoalescedText() throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        String xml = """
                <c:catalog xmlns:c="urn:catalog">
                    <c:item id="book">A &amp; B<![CDATA[ <guide>]]></c:item>
                </c:catalog>
                """;
        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
        try {
            assertEquals(XMLStreamConstants.START_DOCUMENT, reader.getEventType());
            assertEquals(XMLStreamConstants.START_ELEMENT, reader.nextTag());
            assertEquals(new QName("urn:catalog", "catalog"), reader.getName());
            assertEquals("urn:catalog", reader.getNamespaceURI("c"));
            assertEquals(XMLStreamConstants.START_ELEMENT, reader.nextTag());
            assertEquals(new QName("urn:catalog", "item"), reader.getName());
            assertEquals("book", reader.getAttributeValue(null, "id"));
            assertEquals(XMLStreamConstants.CHARACTERS, reader.next());
            assertEquals("A & B <guide>", reader.getText());
            assertEquals(XMLStreamConstants.END_ELEMENT, reader.next());
            assertEquals("item", reader.getLocalName());
            assertEquals(XMLStreamConstants.END_ELEMENT, reader.nextTag());
            assertEquals("catalog", reader.getLocalName());
            assertEquals(XMLStreamConstants.END_DOCUMENT, reader.next());
            assertFalse(reader.hasNext());
        } finally {
            reader.close();
        }
    }

    @Test
    void filtersEventReadersToApplicationElements() throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        String xml = """
                <catalog><!-- inventory --><item id="first">one</item><item id="second">two</item></catalog>
                """;
        XMLEventReader source = factory.createXMLEventReader(new StringReader(xml));
        try {
            XMLEventReader reader = factory.createFilteredReader(source, event -> event.isStartElement()
                    && "item".equals(event.asStartElement().getName().getLocalPart()));
            try {
                List<String> identifiers = new ArrayList<>();
                while (reader.hasNext()) {
                    XMLEvent next = reader.peek();
                    assertTrue(next.isStartElement());
                    StartElement element = reader.nextEvent().asStartElement();
                    identifiers.add(element.getAttributeByName(new QName("id")).getValue());
                }
                assertEquals(List.of("first", "second"), identifiers);
            } finally {
                reader.close();
            }
        } finally {
            source.close();
        }
    }
}
