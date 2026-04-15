/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_istack.istack_commons_runtime;

import com.sun.istack.ByteArrayDataSource;
import com.sun.istack.FinalArrayList;
import com.sun.istack.FragmentContentHandler;
import com.sun.istack.Pool;
import com.sun.istack.SAXException2;
import com.sun.istack.SAXParseException2;
import com.sun.istack.XMLStreamException2;
import com.sun.istack.XMLStreamReaderToContentHandler;
import com.sun.istack.localization.Localizable;
import com.sun.istack.localization.LocalizableMessage;
import com.sun.istack.localization.LocalizableMessageFactory;
import com.sun.istack.localization.Localizer;
import com.sun.istack.localization.NullLocalizable;
import com.sun.istack.logging.Logger;
import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.LocatorImpl;

import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Istack_commons_runtimeTest {

    @Test
    void byteArrayDataSourceUsesProvidedLengthAndBackingBuffer() throws IOException {
        byte[] data = "sample-data".getBytes(StandardCharsets.UTF_8);
        ByteArrayDataSource dataSource = new ByteArrayDataSource(data, 6, null);

        data[0] = 'S';

        try (InputStream inputStream = dataSource.getInputStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("Sample");
        }
        assertThat(dataSource.getContentType()).isEqualTo("application/octet-stream");
        assertThat(dataSource.getName()).isNull();
        assertThatThrownBy(dataSource::getOutputStream).isInstanceOf(UnsupportedOperationException.class);

        ByteArrayDataSource typedDataSource = new ByteArrayDataSource("xml".getBytes(StandardCharsets.UTF_8), "text/plain");
        assertThat(typedDataSource.getContentType()).isEqualTo("text/plain");
    }

    @Test
    void finalArrayListSupportsCommonConstructionPatterns() {
        FinalArrayList<String> fromCollection = new FinalArrayList<>(List.of("alpha", "beta"));
        FinalArrayList<String> withCapacity = new FinalArrayList<>(1);
        FinalArrayList<String> empty = new FinalArrayList<>();

        withCapacity.add("gamma");
        empty.addAll(fromCollection);

        assertThat(fromCollection).containsExactly("alpha", "beta");
        assertThat(withCapacity).containsExactly("gamma");
        assertThat(empty).containsExactly("alpha", "beta");
    }

    @Test
    void fragmentContentHandlerSuppressesDocumentBoundaries() throws SAXException {
        RecordingContentHandler delegate = new RecordingContentHandler();
        FragmentContentHandler handler = new FragmentContentHandler(delegate);
        AttributesImpl attributes = new AttributesImpl();
        char[] text = "body".toCharArray();

        handler.startDocument();
        handler.startElement("urn:test", "item", "item", attributes);
        handler.characters(text, 0, text.length);
        handler.endElement("urn:test", "item", "item");
        handler.endDocument();

        assertThat(delegate.events).containsExactly(
                "startElement:item",
                "characters:body",
                "endElement:item"
        );
    }

    @Test
    void localizerUsesSupplierBundlesCachesThemAndDefensivelyCopiesArguments() {
        AtomicInteger bundleLookups = new AtomicInteger();
        LocalizableMessageFactory factory = new LocalizableMessageFactory(
                "test.bundle",
                locale -> {
                    bundleLookups.incrementAndGet();
                    return new TestMessagesBundle();
                }
        );
        Localizer localizer = new Localizer(Locale.US);
        Localizable nested = factory.getMessage("inner", "world");
        Localizable outer = factory.getMessage("outer", nested, "istack");
        Localizable undefined = factory.getMessage(null, "value");
        LocalizableMessage message = new LocalizableMessage(
                "test.bundle",
                locale -> new TestMessagesBundle(),
                "outer",
                "original"
        );

        Object[] arguments = message.getArguments();
        arguments[0] = "changed";

        assertThat(localizer.localize(outer)).isEqualTo("Hello world! from istack");
        assertThat(localizer.localize(undefined)).isEqualTo("Undefined value");
        assertThat(bundleLookups).hasValue(1);
        assertThat(message.getArguments()).containsExactly("original");
    }

    @Test
    void nullLocalizableAndMissingBundlesFallbackGracefully() {
        Localizer localizer = new Localizer(Locale.US);
        Localizable missing = new LocalizableMessageFactory("missing.bundle", locale -> null)
                .getMessage("missingKey", "alpha", 7);

        assertThat(localizer.localize(new NullLocalizable("plain text"))).isEqualTo("plain text");
        assertThat(localizer.localize(missing)).isEqualTo("[failed to localize] missingKey(alpha, 7)");
        assertThatThrownBy(() -> new NullLocalizable(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void poolImplementationReusesRecycledInstancesAndCreatesOnDemand() {
        AtomicInteger createdInstances = new AtomicInteger();
        Pool.Impl<StringBuilder> pool = new Pool.Impl<>() {
            @Override
            protected StringBuilder create() {
                return new StringBuilder("builder-").append(createdInstances.incrementAndGet());
            }
        };

        StringBuilder first = pool.take();
        first.append("-used");
        pool.recycle(first);

        StringBuilder recycled = pool.take();
        StringBuilder second = pool.take();

        assertThat(recycled).isSameAs(first);
        assertThat(recycled.toString()).isEqualTo("builder-1-used");
        assertThat(second).isNotSameAs(first);
        assertThat(second.toString()).isEqualTo("builder-2");
        assertThat(createdInstances).hasValue(2);
    }

    @Test
    void exceptionWrappersExposeOriginalCauseAndLocation() {
        IOException cause = new IOException("broken");
        SAXException2 saxException = new SAXException2("sax failed", cause);
        LocatorImpl locator = new LocatorImpl();
        locator.setPublicId("public-id");
        locator.setSystemId("system-id");
        locator.setLineNumber(12);
        locator.setColumnNumber(34);
        SAXParseException2 saxParseException = new SAXParseException2("parse failed", locator, cause);
        XMLStreamException2 xmlStreamException = new XMLStreamException2(
                "stream failed",
                new SimpleLocation(7, 9, "public-id", "system-id"),
                cause
        );

        assertThat(saxException).hasMessage("sax failed");
        assertThat(saxException.getCause()).isSameAs(cause);

        assertThat(saxParseException).hasMessageContaining("parse failed");
        assertThat(saxParseException.getCause()).isSameAs(cause);
        assertThat(saxParseException.getPublicId()).isEqualTo("public-id");
        assertThat(saxParseException.getSystemId()).isEqualTo("system-id");
        assertThat(saxParseException.getLineNumber()).isEqualTo(12);
        assertThat(saxParseException.getColumnNumber()).isEqualTo(34);

        assertThat(xmlStreamException).hasMessageContaining("stream failed");
        assertThat(xmlStreamException.getCause()).isSameAs(cause);
        assertThat(xmlStreamException.getLocation().getLineNumber()).isEqualTo(7);
        assertThat(xmlStreamException.getLocation().getColumnNumber()).isEqualTo(9);
        assertThat(xmlStreamException.getLocation().getPublicId()).isEqualTo("public-id");
        assertThat(xmlStreamException.getLocation().getSystemId()).isEqualTo("system-id");
    }

    @Test
    void xmlStreamReaderToContentHandlerBridgesNamespacesAttributesAndNestedContent() throws Exception {
        XMLStreamReader reader = newXmlStreamReader(
                "<root xmlns=\"urn:root\" xmlns:p=\"urn:p\" plain=\"v1\"><?pi inside?><p:child p:flag=\"yes\">text</p:child></root>"
        );
        RecordingContentHandler handler = new RecordingContentHandler();

        new XMLStreamReaderToContentHandler(reader, handler, false, false).bridge();

        assertThat(handler.events).containsExactly(
                "startDocument",
                "startPrefix:=urn:root",
                "startPrefix:p=urn:p",
                "startElement:root",
                "pi:pi=inside",
                "startElement:p:child",
                "characters:text",
                "endElement:p:child",
                "endElement:root",
                "endPrefix:p",
                "endPrefix:",
                "endDocument"
        );
        assertThat(handler.locator).isNotNull();
        assertThat(handler.startElements).containsExactly(
                new StartElementEvent(
                        "urn:root",
                        "root",
                        "root",
                        List.of(new AttributeSnapshot("", "plain", "plain", "CDATA", "v1"))
                ),
                new StartElementEvent(
                        "urn:p",
                        "child",
                        "p:child",
                        List.of(new AttributeSnapshot("urn:p", "flag", "p:flag", "CDATA", "yes"))
                )
        );
        assertThat(reader.getEventType()).isEqualTo(XMLStreamConstants.END_DOCUMENT);
    }

    @Test
    void xmlStreamReaderToContentHandlerSupportsFragmentsEagerQuitAndInScopeNamespaces() throws Exception {
        XMLStreamReader reader = newXmlStreamReader("<root><child/></root>");
        RecordingContentHandler handler = new RecordingContentHandler();

        new XMLStreamReaderToContentHandler(reader, handler, true, true, new String[]{"pre", "urn:pre"}).bridge();

        assertThat(handler.events).containsExactly(
                "startPrefix:pre=urn:pre",
                "startElement:root",
                "startElement:child",
                "endElement:child",
                "endElement:root",
                "endPrefix:pre"
        );
        assertThat(reader.getEventType()).isEqualTo(XMLStreamConstants.END_ELEMENT);
        assertThat(reader.getLocalName()).isEqualTo("root");
    }

    @Test
    void loggerDelegatesToJulAndAssociatesCausesWithLoggedExceptions() {
        String loggerName = "com.sun.istack.tests." + System.nanoTime();
        java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(loggerName);
        RecordingLogHandler handler = new RecordingLogHandler();
        julLogger.setUseParentHandlers(false);
        julLogger.setLevel(Level.ALL);
        handler.setLevel(Level.ALL);
        julLogger.addHandler(handler);

        try {
            Logger logger = Logger.getLogger(loggerName, Istack_commons_runtimeTest.class);
            IllegalArgumentException cause = new IllegalArgumentException("cause");
            IllegalStateException exception = new IllegalStateException("boom");

            logger.setLevel(Level.ALL);
            logger.info("Hello {0}", new Object[]{"istack"});
            assertThat(logger.logException(exception, cause, Level.WARNING)).isSameAs(exception);
            logger.entering("argument");
            logger.exiting("result");

            assertThat(logger.isLoggable(Level.INFO)).isTrue();
            assertThat(logger.isMethodCallLoggable()).isTrue();
            assertThat(exception.getCause()).isSameAs(cause);
            assertThat(handler.records).hasSize(4);
            assertThat(handler.records.get(0).getLevel()).isEqualTo(Level.INFO);
            assertThat(handler.records.get(0).getMessage()).isEqualTo("Hello {0}");
            assertThat(handler.records.get(0).getParameters()).containsExactly("istack");
            assertThat(handler.records.get(1).getLevel()).isEqualTo(Level.WARNING);
            assertThat(handler.records.get(1).getMessage()).isEqualTo("boom");
            assertThat(handler.records.get(1).getThrown()).isSameAs(cause);
            assertThat(handler.records.get(2).getLevel()).isEqualTo(Level.FINER);
            assertThat(handler.records.get(2).getMessage()).startsWith("ENTRY");
            assertThat(handler.records.get(3).getLevel()).isEqualTo(Level.FINER);
            assertThat(handler.records.get(3).getMessage()).startsWith("RETURN");
        } finally {
            julLogger.removeHandler(handler);
        }
    }

    private static XMLStreamReader newXmlStreamReader(String xml) throws Exception {
        XMLInputFactory inputFactory = XMLInputFactory.newFactory();
        inputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        return inputFactory.createXMLStreamReader(new StringReader(xml));
    }

    private record AttributeSnapshot(String uri, String localName, String qName, String type, String value) {
    }

    private record StartElementEvent(String uri, String localName, String qName, List<AttributeSnapshot> attributes) {
    }

    private static final class RecordingContentHandler extends DefaultHandler {
        private final List<String> events = new ArrayList<>();
        private final List<StartElementEvent> startElements = new ArrayList<>();
        private Locator locator;

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        @Override
        public void startDocument() {
            events.add("startDocument");
        }

        @Override
        public void endDocument() {
            events.add("endDocument");
        }

        @Override
        public void startPrefixMapping(String prefix, String uri) {
            events.add("startPrefix:" + prefix + "=" + uri);
        }

        @Override
        public void endPrefixMapping(String prefix) {
            events.add("endPrefix:" + prefix);
        }

        @Override
        public void processingInstruction(String target, String data) {
            events.add("pi:" + target + "=" + data);
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            events.add("startElement:" + qName);
            List<AttributeSnapshot> snapshot = new ArrayList<>();
            for (int index = 0; index < attributes.getLength(); index++) {
                snapshot.add(new AttributeSnapshot(
                        attributes.getURI(index),
                        attributes.getLocalName(index),
                        attributes.getQName(index),
                        attributes.getType(index),
                        attributes.getValue(index)
                ));
            }
            startElements.add(new StartElementEvent(uri, localName, qName, List.copyOf(snapshot)));
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            String text = new String(ch, start, length);
            if (!text.isBlank()) {
                events.add("characters:" + text);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            events.add("endElement:" + qName);
        }
    }

    private static final class RecordingLogHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private static final class SimpleLocation implements Location {
        private final int lineNumber;
        private final int columnNumber;
        private final String publicId;
        private final String systemId;

        private SimpleLocation(int lineNumber, int columnNumber, String publicId, String systemId) {
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
            this.publicId = publicId;
            this.systemId = systemId;
        }

        @Override
        public int getLineNumber() {
            return lineNumber;
        }

        @Override
        public int getColumnNumber() {
            return columnNumber;
        }

        @Override
        public int getCharacterOffset() {
            return -1;
        }

        @Override
        public String getPublicId() {
            return publicId;
        }

        @Override
        public String getSystemId() {
            return systemId;
        }
    }

    private static final class TestMessagesBundle extends ResourceBundle {
        private final Map<String, Object> messages = new LinkedHashMap<>();

        private TestMessagesBundle() {
            messages.put("outer", "Hello {0} from {1}");
            messages.put("inner", "{0}!");
            messages.put("undefined", "Undefined {0}");
        }

        @Override
        protected Object handleGetObject(String key) {
            return messages.get(key);
        }

        @Override
        public Enumeration<String> getKeys() {
            return Collections.enumeration(messages.keySet());
        }
    }
}
