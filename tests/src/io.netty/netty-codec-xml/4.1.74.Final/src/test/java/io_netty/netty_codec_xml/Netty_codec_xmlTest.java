/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_xml;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.xml.XmlAttribute;
import io.netty.handler.codec.xml.XmlCdata;
import io.netty.handler.codec.xml.XmlCharacters;
import io.netty.handler.codec.xml.XmlComment;
import io.netty.handler.codec.xml.XmlContent;
import io.netty.handler.codec.xml.XmlDTD;
import io.netty.handler.codec.xml.XmlDecoder;
import io.netty.handler.codec.xml.XmlDocumentEnd;
import io.netty.handler.codec.xml.XmlDocumentStart;
import io.netty.handler.codec.xml.XmlElementEnd;
import io.netty.handler.codec.xml.XmlElementStart;
import io.netty.handler.codec.xml.XmlEntityReference;
import io.netty.handler.codec.xml.XmlNamespace;
import io.netty.handler.codec.xml.XmlProcessingInstruction;
import io.netty.handler.codec.xml.XmlSpace;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class Netty_codec_xmlTest {
    @Test
    void decoderEmitsDocumentMarkupNamespacesAttributesAndContentEvents() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <?catalog instruction?>
                <!DOCTYPE catalog SYSTEM "catalog.dtd">
                <catalog xmlns="urn:books" xmlns:p="urn:publisher" p:code="bk-001" category="fiction">
                  <book p:id="1">Title <![CDATA[<raw>]]><!--note--></book>
                </catalog>
                """.stripLeading();

        List<Object> events = decode(xml);

        XmlDocumentStart documentStart = documentStart(events);
        assertThat(documentStart.version()).isEqualTo("1.0");
        assertThat(documentStart.standalone()).isTrue();
        assertThat(documentStart.encodingScheme()).isEqualTo("UTF-8");

        XmlProcessingInstruction processingInstruction = processingInstruction(events);
        assertThat(processingInstruction.target()).isEqualTo("catalog");
        assertThat(processingInstruction.data()).isEqualTo("instruction");

        XmlDTD dtd = dtd(events);
        assertThat(dtd.text()).contains("catalog.dtd");

        XmlElementStart catalog = elementStart(events, "catalog");
        assertThat(catalog.namespace()).isEqualTo("urn:books");
        assertThat(catalog.namespaces())
                .extracting(XmlNamespace::uri)
                .contains("urn:books", "urn:publisher");
        assertThat(catalog.attributes())
                .anySatisfy(attribute -> {
                    assertThat(attribute.name()).isEqualTo("code");
                    assertThat(attribute.prefix()).isEqualTo("p");
                    assertThat(attribute.namespace()).isEqualTo("urn:publisher");
                    assertThat(attribute.value()).isEqualTo("bk-001");
                })
                .anySatisfy(attribute -> {
                    assertThat(attribute.name()).isEqualTo("category");
                    assertThat(attribute.value()).isEqualTo("fiction");
                });

        XmlElementStart book = elementStart(events, "book");
        assertThat(book.namespace()).isEqualTo("urn:books");
        assertThat(book.attributes()).singleElement().satisfies(attribute -> {
            assertThat(attribute.name()).isEqualTo("id");
            assertThat(attribute.prefix()).isEqualTo("p");
            assertThat(attribute.value()).isEqualTo("1");
        });

        assertThat(characterData(events)).anySatisfy(text -> assertThat(text).contains("Title"));
        assertThat(cdataData(events)).contains("<raw>");
        assertThat(commentData(events)).contains("note");

        XmlElementEnd bookEnd = elementEnd(events, "book");
        assertThat(bookEnd.namespace()).isEqualTo("urn:books");
    }

    @Test
    void decoderHandlesInputSplitAcrossUtf8ByteBoundaries() {
        byte[] xml = "<root><item>caf\u00e9</item><item>second</item></root>".getBytes(StandardCharsets.UTF_8);

        List<Object> events = decodeInChunks(xml, 1);

        assertThat(structuralEvents(events)).containsExactly(
                "documentStart",
                "start:root",
                "start:item",
                "end:item",
                "start:item",
                "end:item",
                "end:root");
        assertThat(String.join("", characterData(events))).isEqualTo("caf\u00e9second");
    }

    @Test
    void decoderResolvesXmlEntitiesInTextAndAttributes() {
        String xml = """
                <root description="Tom &amp; Jerry &quot;show&quot;">
                  3 &lt; 5 &amp;&amp; 7 &gt; 2&apos;s
                </root>
                """.stripLeading();

        List<Object> events = decode(xml);

        XmlElementStart root = elementStart(events, "root");
        assertThat(root.attributes()).singleElement().satisfies(attribute -> {
            assertThat(attribute.name()).isEqualTo("description");
            assertThat(attribute.value()).isEqualTo("Tom & Jerry \"show\"");
        });
        assertThat(String.join("", characterData(events)).trim()).isEqualTo("3 < 5 && 7 > 2's");
    }

    @Test
    void decoderClosesNamespaceScopeForEmptyElements() {
        String xml = """
                <root>
                  <p:item xmlns:p="urn:item"/>
                </root>
                """.stripLeading();

        List<Object> events = decode(xml);

        XmlElementStart itemStart = elementStart(events, "item");
        assertThat(itemStart.prefix()).isEqualTo("p");
        assertThat(itemStart.namespace()).isEqualTo("urn:item");
        assertThat(itemStart.namespaces()).singleElement().satisfies(namespace -> {
            assertThat(namespace.prefix()).isEqualTo("p");
            assertThat(namespace.uri()).isEqualTo("urn:item");
        });

        XmlElementEnd itemEnd = elementEnd(events, "item");
        assertThat(itemEnd.prefix()).isEqualTo("p");
        assertThat(itemEnd.namespace()).isEqualTo("urn:item");
        assertThat(itemEnd.namespaces()).singleElement().satisfies(namespace -> {
            assertThat(namespace.prefix()).isEqualTo("p");
            assertThat(namespace.uri()).isEqualTo("urn:item");
        });
        assertThat(structuralEvents(events)).containsSubsequence("start:item", "end:item", "end:root");
    }

    @Test
    void xmlValueObjectsExposeStateAndValueSemantics() {
        XmlAttribute attribute = new XmlAttribute("CDATA", "id", "p", "urn:test", "42");
        assertThat(attribute.type()).isEqualTo("CDATA");
        assertThat(attribute.name()).isEqualTo("id");
        assertThat(attribute.prefix()).isEqualTo("p");
        assertThat(attribute.namespace()).isEqualTo("urn:test");
        assertThat(attribute.value()).isEqualTo("42");
        assertThat(attribute)
                .isEqualTo(new XmlAttribute("CDATA", "id", "p", "urn:test", "42"))
                .hasSameHashCodeAs(new XmlAttribute("CDATA", "id", "p", "urn:test", "42"))
                .isNotEqualTo(new XmlAttribute("CDATA", "id", "p", "urn:test", "43"));
        assertThat(attribute.toString()).contains("id", "42");

        XmlNamespace namespace = new XmlNamespace("p", "urn:test");
        assertThat(namespace.prefix()).isEqualTo("p");
        assertThat(namespace.uri()).isEqualTo("urn:test");
        assertThat(namespace)
                .isEqualTo(new XmlNamespace("p", "urn:test"))
                .hasSameHashCodeAs(new XmlNamespace("p", "urn:test"));

        XmlElementStart start = new XmlElementStart("entry", "urn:test", "p");
        start.attributes().add(attribute);
        start.namespaces().add(namespace);
        XmlElementStart sameStart = new XmlElementStart("entry", "urn:test", "p");
        sameStart.attributes().add(new XmlAttribute("CDATA", "id", "p", "urn:test", "42"));
        sameStart.namespaces().add(new XmlNamespace("p", "urn:test"));
        assertThat(start.name()).isEqualTo("entry");
        assertThat(start.namespace()).isEqualTo("urn:test");
        assertThat(start.prefix()).isEqualTo("p");
        assertThat(start.attributes()).containsExactly(attribute);
        assertThat(start.namespaces()).containsExactly(namespace);
        assertThat(start).isEqualTo(sameStart).hasSameHashCodeAs(sameStart);

        XmlElementEnd end = new XmlElementEnd("entry", "urn:test", "p");
        end.namespaces().add(namespace);
        XmlElementEnd sameEnd = new XmlElementEnd("entry", "urn:test", "p");
        sameEnd.namespaces().add(new XmlNamespace("p", "urn:test"));
        assertThat(end).isEqualTo(sameEnd).hasSameHashCodeAs(sameEnd);
        assertThat(end.toString()).contains("entry", "urn:test");

        assertThat(XmlDocumentEnd.INSTANCE).isNotNull();

        XmlDocumentStart documentStart = new XmlDocumentStart("UTF-8", "1.0", true, "UTF-8");
        assertThat(documentStart.encoding()).isEqualTo("UTF-8");
        assertThat(documentStart.version()).isEqualTo("1.0");
        assertThat(documentStart.standalone()).isTrue();
        assertThat(documentStart.encodingScheme()).isEqualTo("UTF-8");
        assertThat(documentStart).isEqualTo(new XmlDocumentStart("UTF-8", "1.0", true, "UTF-8"));

        XmlEntityReference entityReference = new XmlEntityReference("writer", "Donald Duck");
        assertThat(entityReference.name()).isEqualTo("writer");
        assertThat(entityReference.text()).isEqualTo("Donald Duck");
        assertThat(entityReference).isEqualTo(new XmlEntityReference("writer", "Donald Duck"));

        assertContent(new XmlCharacters("text"), new XmlCharacters("text"), "text");
        assertContent(new XmlCdata("<raw>"), new XmlCdata("<raw>"), "<raw>");
        assertContent(new XmlComment("note"), new XmlComment("note"), "note");
        assertContent(new XmlSpace("  "), new XmlSpace("  "), "  ");
        assertThat(new XmlCharacters("same data")).isNotEqualTo(new XmlCdata("same data"));

        XmlDTD dtd = new XmlDTD("<!ELEMENT entry (#PCDATA)>");
        assertThat(dtd.text()).contains("ELEMENT entry");
        assertThat(dtd).isEqualTo(new XmlDTD("<!ELEMENT entry (#PCDATA)>"));

        XmlProcessingInstruction processingInstruction = new XmlProcessingInstruction(
                "href=\"style.css\"", "xml-stylesheet");
        assertThat(processingInstruction.data()).isEqualTo("href=\"style.css\"");
        assertThat(processingInstruction.target()).isEqualTo("xml-stylesheet");
        assertThat(processingInstruction)
                .isEqualTo(new XmlProcessingInstruction("href=\"style.css\"", "xml-stylesheet"));
    }

    private static void assertContent(XmlContent actual, XmlContent expected, String data) {
        assertThat(actual.data()).isEqualTo(data);
        assertThat(actual).isEqualTo(expected).hasSameHashCodeAs(expected);
        assertThat(actual.toString()).contains(data);
    }

    private static List<Object> decode(String xml) {
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        return decodeInChunks(bytes, bytes.length);
    }

    private static List<Object> decodeInChunks(byte[] bytes, int chunkSize) {
        EmbeddedChannel channel = new EmbeddedChannel(new XmlDecoder());
        List<Object> events = new ArrayList<>();
        try {
            for (int offset = 0; offset < bytes.length; offset += chunkSize) {
                int length = Math.min(chunkSize, bytes.length - offset);
                channel.writeInbound(Unpooled.wrappedBuffer(bytes, offset, length));
                drainInbound(channel, events);
            }
            channel.finish();
            drainInbound(channel, events);
            return events;
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static void drainInbound(EmbeddedChannel channel, List<Object> events) {
        Object event;
        while ((event = channel.readInbound()) != null) {
            events.add(event);
        }
    }

    private static XmlElementStart elementStart(List<Object> events, String name) {
        for (Object event : events) {
            if (event instanceof XmlElementStart start && start.name().equals(name)) {
                return start;
            }
        }
        fail("Expected start element <%s> in %s", name, events);
        return null;
    }

    private static XmlElementEnd elementEnd(List<Object> events, String name) {
        for (Object event : events) {
            if (event instanceof XmlElementEnd end && end.name().equals(name)) {
                return end;
            }
        }
        fail("Expected end element </%s> in %s", name, events);
        return null;
    }

    private static XmlDocumentStart documentStart(List<Object> events) {
        for (Object event : events) {
            if (event instanceof XmlDocumentStart documentStart) {
                return documentStart;
            }
        }
        fail("Expected document start event in %s", events);
        return null;
    }

    private static XmlProcessingInstruction processingInstruction(List<Object> events) {
        for (Object event : events) {
            if (event instanceof XmlProcessingInstruction processingInstruction) {
                return processingInstruction;
            }
        }
        fail("Expected processing instruction event in %s", events);
        return null;
    }

    private static XmlDTD dtd(List<Object> events) {
        for (Object event : events) {
            if (event instanceof XmlDTD dtd) {
                return dtd;
            }
        }
        fail("Expected DTD event in %s", events);
        return null;
    }

    private static List<String> characterData(List<Object> events) {
        List<String> data = new ArrayList<>();
        for (Object event : events) {
            if (event instanceof XmlCharacters characters) {
                data.add(characters.data());
            }
        }
        return data;
    }

    private static List<String> cdataData(List<Object> events) {
        List<String> data = new ArrayList<>();
        for (Object event : events) {
            if (event instanceof XmlCdata cdata) {
                data.add(cdata.data());
            }
        }
        return data;
    }

    private static List<String> commentData(List<Object> events) {
        List<String> data = new ArrayList<>();
        for (Object event : events) {
            if (event instanceof XmlComment comment) {
                data.add(comment.data());
            }
        }
        return data;
    }

    private static List<String> structuralEvents(List<Object> events) {
        List<String> structuralEvents = new ArrayList<>();
        for (Object event : events) {
            if (event instanceof XmlDocumentStart) {
                structuralEvents.add("documentStart");
            } else if (event instanceof XmlElementStart start) {
                structuralEvents.add("start:" + start.name());
            } else if (event instanceof XmlElementEnd end) {
                structuralEvents.add("end:" + end.name());
            } else if (event == XmlDocumentEnd.INSTANCE) {
                structuralEvents.add("documentEnd");
            }
        }
        return structuralEvents;
    }
}
