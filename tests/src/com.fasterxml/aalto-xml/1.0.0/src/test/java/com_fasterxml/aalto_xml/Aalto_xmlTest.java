/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml.aalto_xml;

import com.fasterxml.aalto.stax.EventFactoryImpl;
import com.fasterxml.aalto.stax.InputFactoryImpl;
import com.fasterxml.aalto.stax.OutputFactoryImpl;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Aalto_xmlTest {
    @Test
    void xmlInputFactoryLoadsAaltoProviderAndParsesXml() throws Exception {
        XMLInputFactory inputFactory = XMLInputFactory.newFactory();

        assertThat(inputFactory).isInstanceOf(InputFactoryImpl.class);

        byte[] xmlBytes = "<root attribute='value'>hello</root>".getBytes(StandardCharsets.UTF_8);
        XMLStreamReader streamReader = inputFactory.createXMLStreamReader(new ByteArrayInputStream(xmlBytes));

        assertThat(streamReader.nextTag()).isEqualTo(XMLStreamConstants.START_ELEMENT);
        assertThat(streamReader.getLocalName()).isEqualTo("root");
        assertThat(streamReader.getAttributeValue(null, "attribute")).isEqualTo("value");
        assertThat(streamReader.next()).isEqualTo(XMLStreamConstants.CHARACTERS);
        assertThat(streamReader.getText()).isEqualTo("hello");
        assertThat(streamReader.next()).isEqualTo(XMLStreamConstants.END_ELEMENT);

        streamReader.close();
    }

    @Test
    void xmlInputFactoryHandlesNamespaceBindingGrowth() throws Exception {
        XMLInputFactory inputFactory = XMLInputFactory.newFactory();

        assertThat(inputFactory).isInstanceOf(InputFactoryImpl.class);

        StringBuilder xmlBuilder = new StringBuilder("<root");
        for (int i = 0; i < 20; i++) {
            xmlBuilder.append(" xmlns:n").append(i).append("='urn:test:").append(i).append("'");
        }
        xmlBuilder.append(">");
        for (int i = 0; i < 20; i++) {
            xmlBuilder.append("<n").append(i).append(":item>");
            xmlBuilder.append(i);
            xmlBuilder.append("</n").append(i).append(":item>");
        }
        xmlBuilder.append("</root>");

        byte[] xmlBytes = xmlBuilder.toString().getBytes(StandardCharsets.UTF_8);
        XMLStreamReader streamReader = inputFactory.createXMLStreamReader(new ByteArrayInputStream(xmlBytes));

        int namespaceAwareElementCount = 0;
        while (streamReader.hasNext()) {
            int eventType = streamReader.next();
            if (eventType == XMLStreamConstants.START_ELEMENT && !"root".equals(streamReader.getLocalName())) {
                assertThat(streamReader.getNamespaceURI()).startsWith("urn:test:");
                namespaceAwareElementCount++;
            }
        }

        assertThat(namespaceAwareElementCount).isEqualTo(20);

        streamReader.close();
    }

    @Test
    void xmlOutputAndEventFactoriesLoadAaltoProvidersAndWriteXml() throws Exception {
        XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();
        XMLEventFactory eventFactory = XMLEventFactory.newFactory();

        assertThat(outputFactory).isInstanceOf(OutputFactoryImpl.class);
        assertThat(eventFactory).isInstanceOf(EventFactoryImpl.class);

        StringWriter stringWriter = new StringWriter();
        XMLEventWriter eventWriter = outputFactory.createXMLEventWriter(stringWriter);

        eventWriter.add(eventFactory.createStartDocument("UTF-8", "1.0"));
        eventWriter.add(eventFactory.createStartElement(new QName("message"), null, null));
        eventWriter.add(eventFactory.createCharacters("native-image"));
        eventWriter.add(eventFactory.createEndElement(new QName("message"), null));
        eventWriter.add(eventFactory.createEndDocument());
        eventWriter.close();

        assertThat(stringWriter.toString()).contains("message").contains("native-image");
    }

    @Test
    void xmlEventReaderUsesAaltoInputFactoryForEventIteration() throws Exception {
        XMLInputFactory inputFactory = XMLInputFactory.newFactory();

        assertThat(inputFactory).isInstanceOf(InputFactoryImpl.class);

        byte[] xmlBytes = "<items><item>one</item><item>two</item></items>".getBytes(StandardCharsets.UTF_8);
        XMLEventReader eventReader = inputFactory.createXMLEventReader(new ByteArrayInputStream(xmlBytes));

        int startElementCount = 0;
        String lastItemValue = null;
        while (eventReader.hasNext()) {
            javax.xml.stream.events.XMLEvent event = eventReader.nextEvent();
            if (event.isStartElement()) {
                startElementCount++;
            }
            if (event.isCharacters() && !event.asCharacters().isWhiteSpace()) {
                lastItemValue = event.asCharacters().getData();
            }
        }

        assertThat(startElementCount).isEqualTo(3);
        assertThat(lastItemValue).isEqualTo("two");

        eventReader.close();
    }
}
