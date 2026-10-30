/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_xml;

import groovy.namespace.QName;
import groovy.xml.NamespaceBuilder;
import groovy.xml.NamespaceBuilderSupport;
import groovy.xml.SAXBuilder;
import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SAXBuilderTest {
    @Test
    void streamsBuilderCallsAsSaxEvents() {
        RecordingHandler handler = new RecordingHandler();
        SAXBuilder builder = new SAXBuilder(handler);

        builder.invokeMethod("catalog", new Object[] {Map.of("region", "EU"), new BuilderBody(this, () -> {
            builder.invokeMethod("book", new Object[] {Map.of("id", "b1"), "Groovy"});
            builder.invokeMethod("book", new Object[] {"Native Image"});
        })});

        assertThat(handler.events).containsExactly(
                "start catalog region=EU",
                "start book id=b1",
                "text Groovy",
                "end book",
                "start book",
                "text Native Image",
                "end book",
                "end catalog");
    }

    @Test
    void reportsNamespaceUrisForQualifiedNames() {
        RecordingHandler handler = new RecordingHandler();
        NamespaceBuilderSupport books = NamespaceBuilder.newInstance(new SAXBuilder(handler), "urn:books", "bk");

        books.invokeMethod("catalog", new Object[] {new LinkedHashMap<>(Map.of(new QName("urn:meta", "id", "m"), "c1")), new BuilderBody(this, () -> {
            books.invokeMethod("book", new Object[] {"Groovy"});
        })});

        assertThat(handler.events).containsExactly(
                "start {urn:books}bk:catalog m:id=c1",
                "start {urn:books}bk:book",
                "text Groovy",
                "end {urn:books}bk:book",
                "end {urn:books}bk:catalog");
        assertThat(handler.attributeUris).containsExactly("urn:meta");
    }

    private static final class RecordingHandler extends DefaultHandler {
        private final List<String> events = new ArrayList<>();
        private final List<String> attributeUris = new ArrayList<>();

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            StringBuilder event = new StringBuilder("start ").append(qualified(uri, qName));
            for (int i = 0; i < attributes.getLength(); i++) {
                event.append(' ').append(attributes.getQName(i)).append('=').append(attributes.getValue(i));
                if (!attributes.getURI(i).isEmpty()) {
                    attributeUris.add(attributes.getURI(i));
                }
            }
            events.add(event.toString());
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            events.add("text " + new String(ch, start, length));
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            events.add("end " + qualified(uri, qName));
        }

        private static String qualified(String uri, String qName) {
            return uri.isEmpty() ? qName : "{" + uri + "}" + qName;
        }
    }
}
