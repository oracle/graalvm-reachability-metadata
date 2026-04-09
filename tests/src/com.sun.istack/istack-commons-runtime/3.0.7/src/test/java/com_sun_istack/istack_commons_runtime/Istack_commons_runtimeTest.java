/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_istack.istack_commons_runtime;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

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
import com.sun.istack.localization.NullLocalizable;
import com.sun.istack.localization.Localizer;
import com.sun.istack.logging.Logger;

import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Istack_commons_runtimeTest {

    @Test
    void finalArrayListConstructorsCreateMutableCopies() {
        List<String> seed = List.of("alpha", "beta");
        FinalArrayList<String> copied = new FinalArrayList<>(seed);
        FinalArrayList<String> sized = new FinalArrayList<>(1);
        FinalArrayList<String> empty = new FinalArrayList<>();

        copied.add("gamma");
        sized.add("delta");
        empty.add("epsilon");

        assertThat(copied).containsExactly("alpha", "beta", "gamma");
        assertThat(sized).containsExactly("delta");
        assertThat(empty).containsExactly("epsilon");
    }

    @Test
    void poolImplementationCreatesAndReusesInstances() {
        TrackingPool pool = new TrackingPool();

        String first = pool.take();
        String second = pool.take();

        pool.recycle(first);
        pool.recycle(second);

        assertThat(pool.take()).isSameAs(first);
        assertThat(pool.take()).isSameAs(second);
        assertThat(pool.getCreatedCount()).isEqualTo(2);
    }

    @Test
    void byteArrayDataSourceExposesConfiguredContentAndBounds() throws Exception {
        byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
        ByteArrayDataSource bounded = new ByteArrayDataSource(payload, 4, null);
        ByteArrayDataSource full = new ByteArrayDataSource(payload, "text/plain");

        try (InputStream inputStream = bounded.getInputStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("payl");
        }

        try (InputStream inputStream = full.getInputStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("payload");
        }

        assertThat(bounded.getContentType()).isEqualTo("application/octet-stream");
        assertThat(full.getContentType()).isEqualTo("text/plain");
        assertThat(full.getName()).isNull();
        assertThatThrownBy(full::getOutputStream).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void fragmentContentHandlerSuppressesDocumentCallbacksButForwardsContentEvents() throws Exception {
        RecordingContentHandler delegate = new RecordingContentHandler();
        FragmentContentHandler fragmentHandler = new FragmentContentHandler(delegate);
        AttributesImpl attributes = new AttributesImpl();

        attributes.addAttribute("", "id", "id", "CDATA", "7");
        fragmentHandler.startDocument();
        fragmentHandler.startElement("urn:test", "item", "item", attributes);
        fragmentHandler.characters("ok".toCharArray(), 0, 2);
        fragmentHandler.endElement("urn:test", "item", "item");
        fragmentHandler.endDocument();

        assertThat(delegate.events).containsExactly(
            "startElement:item|urn:test|item|[id=7@]",
            "characters:ok",
            "endElement:item|urn:test|item"
        );
    }

    @Test
    void wrappedExceptionsExposeNestedCauses() {
        IllegalArgumentException cause = new IllegalArgumentException("broken");
        XMLStreamException2 xmlException = new XMLStreamException2("xml", new FixedLocation(3, 7), cause);
        SAXException2 saxException = new SAXException2("sax", cause);
        SAXParseException2 saxParseException = new SAXParseException2("parse", "public", "system", 5, 9, cause);

        assertThat(xmlException.getCause()).isSameAs(cause);
        assertThat(saxException.getCause()).isSameAs(cause);
        assertThat(saxParseException.getCause()).isSameAs(cause);
        assertThat(saxParseException.getLineNumber()).isEqualTo(5);
        assertThat(saxParseException.getColumnNumber()).isEqualTo(9);
    }

    @Test
    void localizerFallsBackToDefaultMessagesWhenBundlesAreUnavailable() {
        Localizer localizer = new Localizer(Locale.US);
        LocalizableMessage message = new LocalizableMessage("missing.bundle", "outer", "value", 7);

        assertThat(message.getKey()).isEqualTo("outer");
        assertThat(message.getArguments()).containsExactly("value", 7);
        assertThat(message.getResourceBundleName()).isEqualTo("missing.bundle");
        assertThat(message.getResourceBundle(Locale.US)).isNull();
        assertThat(localizer.localize(message)).isEqualTo("[failed to localize] outer(value, 7)");
        assertThat(localizer.localize(new NullLocalizable("literal"))).isEqualTo("literal");
    }

    @Test
    void localizerResolvesSuppliedBundlesNestedMessagesAndUndefinedKeys() {
        AtomicInteger supplierCalls = new AtomicInteger();
        LocalizableMessageFactory messageFactory = new LocalizableMessageFactory(
            "test.bundle",
            locale -> {
                supplierCalls.incrementAndGet();
                return new TestMessages();
            }
        );
        Localizer localizer = new Localizer(Locale.US);
        Localizable nestedMessage = messageFactory.getMessage("inner", "payload");
        Localizable outerMessage = messageFactory.getMessage("outer", nestedMessage, 9);
        Localizable missingMessage = messageFactory.getMessage("missing", "fallback");

        assertThat(localizer.localize(outerMessage)).isEqualTo("Outer sees Inner payload and 9");
        assertThat(localizer.localize(missingMessage)).isEqualTo("Undefined fallback");
        assertThat(supplierCalls).hasValue(1);
    }

    @Test
    void xmlStreamReaderBridgeEmitsNamespacesAttributesAndStopsAtRootEndWhenEagerQuitIsEnabled() throws Exception {
        XMLInputFactory inputFactory = XMLInputFactory.newFactory();
        XMLStreamReader reader = inputFactory.createXMLStreamReader(new StringReader(
            "<?xml version='1.0'?><root xmlns='urn:root' xmlns:p='urn:child' p:id='7'><p:child>value</p:child></root><!--tail-->"
        ));
        RecordingContentHandler handler = new RecordingContentHandler();
        XMLStreamReaderToContentHandler bridge =
            new XMLStreamReaderToContentHandler(reader, handler, true, false, new String[] {"extra", "urn:extra"});

        bridge.bridge();

        assertThat(handler.events).containsExactly(
            "locator",
            "startDocument",
            "startPrefixMapping:extra=urn:extra",
            "startPrefixMapping:=urn:root",
            "startPrefixMapping:p=urn:child",
            "startElement:root|urn:root|root|[p:id=7@urn:child]",
            "startElement:p:child|urn:child|child|[]",
            "characters:value",
            "endElement:p:child|urn:child|child",
            "endElement:root|urn:root|root",
            "endPrefixMapping:p",
            "endPrefixMapping:",
            "endPrefixMapping:extra",
            "endDocument"
        );
        assertThat(reader.getEventType()).isEqualTo(XMLStreamConstants.END_ELEMENT);
        assertThat(reader.next()).isEqualTo(XMLStreamConstants.COMMENT);
    }

    @Test
    void xmlStreamReaderBridgeOmitsDocumentEventsInFragmentMode() throws Exception {
        XMLInputFactory inputFactory = XMLInputFactory.newFactory();
        XMLStreamReader reader =
            inputFactory.createXMLStreamReader(new StringReader("<root xmlns='urn:test'>text</root>"));
        RecordingContentHandler handler = new RecordingContentHandler();
        XMLStreamReaderToContentHandler bridge =
            new XMLStreamReaderToContentHandler(reader, handler, false, true);

        bridge.bridge();

        assertThat(handler.events).containsExactly(
            "startPrefixMapping:=urn:test",
            "startElement:root|urn:test|root|[]",
            "characters:text",
            "endElement:root|urn:test|root",
            "endPrefixMapping:"
        );
    }

    @Test
    void loggerWritesStructuredRecordsAndReturnsLoggedException() {
        String loggerName = "test.istack." + UUID.randomUUID();
        java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(loggerName);
        TestLogHandler logHandler = new TestLogHandler();
        Logger logger = Logger.getLogger(loggerName, Istack_commons_runtimeTest.class);
        IllegalStateException cause = new IllegalStateException("cause");
        RuntimeException failure = new RuntimeException("failure");

        julLogger.setUseParentHandlers(false);
        julLogger.addHandler(logHandler);
        logger.setLevel(Level.FINEST);

        try {
            logger.entering("alpha", 7);
            logger.info("message {0}", new Object[] {"payload"});
            assertThat(logger.logException(failure, cause, Level.WARNING)).isSameAs(failure);
        } finally {
            julLogger.removeHandler(logHandler);
        }

        assertThat(logger.isMethodCallLoggable()).isTrue();
        assertThat(logger.isLoggable(Level.INFO)).isTrue();
        assertThat(failure.getCause()).isSameAs(cause);
        assertThat(logHandler.records).extracting(LogRecord::getLevel)
            .containsExactly(Level.FINER, Level.INFO, Level.WARNING);
        assertThat(logHandler.records).extracting(LogRecord::getMessage)
            .containsExactly("ENTRY {0} {1}", "message {0}", "failure");
        assertThat(logHandler.records.get(1).getParameters()).containsExactly("payload");
        assertThat(logHandler.records.get(2).getThrown()).isSameAs(cause);
    }

    private static final class TrackingPool extends Pool.Impl<String> {

        private final AtomicInteger createdCount = new AtomicInteger();

        @Override
        protected String create() {
            return "created-" + this.createdCount.incrementAndGet();
        }

        int getCreatedCount() {
            return this.createdCount.get();
        }
    }

    private static final class RecordingContentHandler extends DefaultHandler {

        private final List<String> events = new ArrayList<>();

        @Override
        public void setDocumentLocator(Locator locator) {
            this.events.add("locator");
        }

        @Override
        public void startDocument() {
            this.events.add("startDocument");
        }

        @Override
        public void endDocument() {
            this.events.add("endDocument");
        }

        @Override
        public void startPrefixMapping(String prefix, String uri) {
            this.events.add("startPrefixMapping:" + prefix + "=" + uri);
        }

        @Override
        public void endPrefixMapping(String prefix) {
            this.events.add("endPrefixMapping:" + prefix);
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            this.events.add("startElement:" + qName + "|" + uri + "|" + localName + "|" + formatAttributes(attributes));
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            this.events.add("endElement:" + qName + "|" + uri + "|" + localName);
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            this.events.add("characters:" + new String(ch, start, length));
        }

        private String formatAttributes(Attributes attributes) {
            List<String> values = new ArrayList<>();

            for (int i = 0; i < attributes.getLength(); i++) {
                values.add(attributes.getQName(i) + "=" + attributes.getValue(i) + "@" + attributes.getURI(i));
            }
            return values.toString();
        }
    }

    private static final class FixedLocation implements Location {

        private final int lineNumber;
        private final int columnNumber;

        private FixedLocation(int lineNumber, int columnNumber) {
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
        }

        @Override
        public int getLineNumber() {
            return this.lineNumber;
        }

        @Override
        public int getColumnNumber() {
            return this.columnNumber;
        }

        @Override
        public int getCharacterOffset() {
            return -1;
        }

        @Override
        public String getPublicId() {
            return "public";
        }

        @Override
        public String getSystemId() {
            return "system";
        }
    }

    private static final class TestLogHandler extends Handler {

        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            this.records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private static final class TestMessages extends java.util.ResourceBundle {

        private static final Map<String, String> MESSAGES = Map.of(
            "inner", "Inner {0}",
            "outer", "Outer sees {0} and {1}",
            "undefined", "Undefined {0}"
        );

        @Override
        protected Object handleGetObject(String key) {
            return MESSAGES.get(key);
        }

        @Override
        public Enumeration<String> getKeys() {
            return Collections.enumeration(MESSAGES.keySet());
        }
    }
}
