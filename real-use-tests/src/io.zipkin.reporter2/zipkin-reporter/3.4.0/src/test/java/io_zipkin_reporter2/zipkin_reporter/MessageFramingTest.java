/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_zipkin_reporter2.zipkin_reporter;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.reporter.BytesEncoder;
import zipkin2.reporter.BytesMessageEncoder;
import zipkin2.reporter.Encoding;
import zipkin2.reporter.SpanBytesEncoder;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Frames encoded spans into size-bounded transport messages with `BytesMessageEncoder`, sized
 * ahead of time through `Encoding` list-size accounting.
 *
 * The application exports a backlog of spans synchronously, as a batch job or a log shipper
 * would, without an `AsyncReporter`: it encodes each span with the `SpanBytesEncoder` for the
 * transport's encoding, packs as many spans per message as the transport's byte budget allows,
 * and frames each message. A collector decodes every message back into the original spans, in
 * JSON, Proto3 and Thrift alike.
 */
public class MessageFramingTest {

    private static final int MESSAGE_BUDGET = 1_024;
    private static final Endpoint INVENTORY = Endpoint.newBuilder()
            .serviceName("inventory")
            .ip("10.0.0.12")
            .port(9411)
            .build();

    @Test
    void jsonBacklogIsSplitIntoFramedListsWithinTheBudget() {
        assertBacklogRoundTrips(Encoding.JSON, SpanBytesDecoder.JSON_V2);
        assertEquals("application/json", Encoding.JSON.mediaType());
    }

    @Test
    void proto3BacklogIsSplitIntoFramedListsWithinTheBudget() {
        assertBacklogRoundTrips(Encoding.PROTO3, SpanBytesDecoder.PROTO3);
        assertEquals("application/x-protobuf", Encoding.PROTO3.mediaType());
    }

    @Test
    void thriftBacklogIsSplitIntoFramedListsWithinTheBudget() {
        assertBacklogRoundTrips(Encoding.THRIFT, SpanBytesDecoder.THRIFT);
        assertEquals("application/x-thrift", Encoding.THRIFT.mediaType());
    }

    @Test
    void jsonFramingMatchesTheEncodingsOwnListFormat() {
        List<byte[]> encoded = new ArrayList<>();
        for (Span span : backlog(3)) {
            encoded.add(SpanBytesEncoder.JSON_V2.encode(span));
        }

        byte[] framed = BytesMessageEncoder.JSON.encode(encoded);
        String body = new String(framed, UTF_8);

        assertTrue(body.startsWith("[{") && body.endsWith("}]"), body);
        assertEquals(3, body.split("\\},\\{").length, "spans are comma separated");
        assertEquals(new String(Encoding.JSON.encode(encoded), UTF_8), body);
        assertEquals("[]", new String(BytesMessageEncoder.JSON.encode(new ArrayList<>()),
                UTF_8));
    }

    @Test
    void spanLargerThanTheBudgetIsRejectedBeforeFraming() {
        Span oversized = span(1).toBuilder()
                .putTag("payload", "x".repeat(MESSAGE_BUDGET))
                .build();
        SpanBatchExporter exporter = new SpanBatchExporter(Encoding.JSON, MESSAGE_BUDGET);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> exporter.export(List.of(oversized)));

        assertTrue(error.getMessage().contains("exceeds"), error.getMessage());
    }

    private static void assertBacklogRoundTrips(Encoding encoding, SpanBytesDecoder decoder) {
        List<Span> backlog = backlog(40);
        SpanBatchExporter exporter = new SpanBatchExporter(encoding, MESSAGE_BUDGET);

        List<byte[]> messages = exporter.export(backlog);

        assertTrue(messages.size() > 1, "the backlog does not fit one message");
        List<Span> collected = new ArrayList<>();
        for (byte[] message : messages) {
            assertTrue(message.length <= MESSAGE_BUDGET,
                    encoding + " message of " + message.length + " bytes");
            List<Span> decoded = decoder.decodeList(message);
            assertTrue(!decoded.isEmpty(), "every message carries spans");
            collected.addAll(decoded);
        }
        assertEquals(backlog, collected);
    }

    private static List<Span> backlog(int count) {
        List<Span> spans = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            spans.add(span(i));
        }
        return spans;
    }

    private static Span span(int index) {
        return Span.newBuilder()
                .traceId("4e441824ec2b6a44")
                .parentId("4e441824ec2b6a44")
                .id(String.format("%016x", 0x1000L + index))
                .name("reserve-stock")
                .kind(Span.Kind.CLIENT)
                .localEndpoint(INVENTORY)
                .timestamp(1_700_000_000_000_000L + index * 1_000L)
                .duration(250L + index)
                .putTag("sku", "SKU-" + index)
                .build();
    }

    /**
     * Encodes spans for one transport encoding and packs them into framed messages that never
     * exceed the transport's byte budget.
     */
    static final class SpanBatchExporter {

        private final Encoding encoding;
        private final int messageMaxBytes;
        private final BytesEncoder<Span> spanEncoder;
        private final BytesMessageEncoder messageEncoder;

        SpanBatchExporter(Encoding encoding, int messageMaxBytes) {
            this.encoding = encoding;
            this.messageMaxBytes = messageMaxBytes;
            this.spanEncoder = SpanBytesEncoder.forEncoding(encoding);
            this.messageEncoder = BytesMessageEncoder.forEncoding(encoding);
            assertSame(encoding, spanEncoder.encoding());
        }

        List<byte[]> export(List<Span> spans) {
            List<byte[]> messages = new ArrayList<>();
            List<byte[]> pending = new ArrayList<>();
            for (Span span : spans) {
                byte[] encoded = spanEncoder.encode(span);
                assertEquals(spanEncoder.sizeInBytes(span), encoded.length);
                if (encoding.listSizeInBytes(encoded.length) > messageMaxBytes) {
                    throw new IllegalArgumentException(
                            "span " + span.id() + " exceeds the message budget");
                }
                pending.add(encoded);
                if (encoding.listSizeInBytes(pending) > messageMaxBytes) {
                    pending.remove(pending.size() - 1);
                    messages.add(frame(pending));
                    pending = new ArrayList<>();
                    pending.add(encoded);
                }
            }
            if (!pending.isEmpty()) {
                messages.add(frame(pending));
            }
            return messages;
        }

        private byte[] frame(List<byte[]> encodedSpans) {
            byte[] message = messageEncoder.encode(encodedSpans);
            assertEquals(encoding.listSizeInBytes(encodedSpans), message.length);
            return message;
        }
    }
}
