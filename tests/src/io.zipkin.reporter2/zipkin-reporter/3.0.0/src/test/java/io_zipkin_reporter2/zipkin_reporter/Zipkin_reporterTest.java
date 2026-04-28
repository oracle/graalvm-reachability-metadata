/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_zipkin_reporter2.zipkin_reporter;

import org.junit.jupiter.api.Test;
import zipkin2.Call;
import zipkin2.CheckResult;
import zipkin2.Span;
import zipkin2.codec.BytesEncoder;
import zipkin2.codec.Encoding;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.AwaitableCallback;
import zipkin2.reporter.BytesMessageEncoder;
import zipkin2.reporter.InMemoryReporterMetrics;
import zipkin2.reporter.Sender;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Zipkin_reporterTest {

    private static final Span FIRST_SPAN = span(
            "463ac35c9f6413ad",
            "a2fb4a1d1a96d312",
            "client",
            "inventory");
    private static final Span SECOND_SPAN = span(
            "463ac35c9f6413ae",
            "b7ad6b7169203331",
            "client",
            "payments");

    @Test
    void asyncReporterFlushesJsonSpansInBatchesAndTracksMetrics() {
        CapturingSender sender = new CapturingSender(Encoding.JSON, Integer.MAX_VALUE);
        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();
        int firstSpanMessageBytes = sender.messageSizeInBytes(List.of(SpanBytesEncoder.JSON_V2.encode(FIRST_SPAN)));
        int secondSpanMessageBytes = sender.messageSizeInBytes(List.of(SpanBytesEncoder.JSON_V2.encode(SECOND_SPAN)));

        try (AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .metrics(metrics)
                .messageMaxBytes(firstSpanMessageBytes)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .build()) {
            reporter.report(FIRST_SPAN);
            reporter.report(SECOND_SPAN);

            reporter.flush();

            assertThat(sender.sentBatches()).containsExactly(List.of(FIRST_SPAN));
            assertThat(metrics.spans()).isEqualTo(2);
            assertThat(metrics.spanBytes()).isEqualTo(encodedSize(FIRST_SPAN) + encodedSize(SECOND_SPAN));
            assertThat(metrics.messages()).isEqualTo(1);
            assertThat(metrics.messageBytes()).isEqualTo(firstSpanMessageBytes);
            assertThat(metrics.queuedSpans()).isEqualTo(1);
            assertThat(metrics.queuedBytes()).isEqualTo(encodedSize(SECOND_SPAN));
            assertThat(metrics.spansDropped()).isZero();

            reporter.flush();

            assertThat(sender.sentBatches()).containsExactly(List.of(FIRST_SPAN), List.of(SECOND_SPAN));
            assertThat(metrics.messages()).isEqualTo(2);
            assertThat(metrics.messageBytes()).isEqualTo((long) firstSpanMessageBytes + secondSpanMessageBytes);
            assertThat(metrics.queuedSpans()).isZero();
            assertThat(metrics.queuedBytes()).isZero();
        }
    }

    @Test
    void asyncReporterDropsSpansThatCannotFitIntoAConfiguredMessage() {
        CapturingSender sender = new CapturingSender(Encoding.JSON, Integer.MAX_VALUE);
        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();
        int singleSpanMessageBytes = sender.messageSizeInBytes(List.of(SpanBytesEncoder.JSON_V2.encode(FIRST_SPAN)));
        Span oversizedSpan = FIRST_SPAN.toBuilder()
                .putTag("payload", "x".repeat(4096))
                .build();

        try (AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .metrics(metrics)
                .messageMaxBytes(singleSpanMessageBytes)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .build()) {
            reporter.report(oversizedSpan);
            reporter.flush();

            assertThat(sender.sentBatches()).containsExactly(List.of());
            assertThat(metrics.spans()).isEqualTo(1);
            assertThat(metrics.spansDropped()).isEqualTo(1);
            assertThat(metrics.messages()).isEqualTo(1);
            assertThat(metrics.messageBytes()).isEqualTo(2);
            assertThat(metrics.queuedSpans()).isZero();
            assertThat(metrics.queuedBytes()).isZero();
        }
    }

    @Test
    void asyncReporterDropsSpansWhenQueueCapacityIsExceeded() {
        CapturingSender sender = new CapturingSender(Encoding.JSON, Integer.MAX_VALUE);
        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();

        try (AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .metrics(metrics)
                .queuedMaxSpans(1)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .build()) {
            reporter.report(FIRST_SPAN);
            reporter.report(SECOND_SPAN);
            reporter.flush();

            assertThat(sender.sentBatches()).containsExactly(List.of(FIRST_SPAN));
            assertThat(metrics.spans()).isEqualTo(2);
            assertThat(metrics.spansDropped()).isEqualTo(1);
            assertThat(metrics.messages()).isEqualTo(1);
        }
    }

    @Test
    void asyncReporterDropsPendingSpansOnCloseAndRejectsFutureFlushes() {
        CapturingSender sender = new CapturingSender(Encoding.JSON, Integer.MAX_VALUE);
        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();
        AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .metrics(metrics)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .build();

        reporter.report(FIRST_SPAN);
        reporter.close();

        assertThat(sender.sentBatches()).isEmpty();
        assertThat(metrics.spansDropped()).isEqualTo(1);

        reporter.report(SECOND_SPAN);

        assertThat(metrics.spansDropped()).isEqualTo(2);
        assertThatThrownBy(reporter::flush).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void asyncReporterUsesProto3EncodingWhenRequiredByTheSender() {
        CapturingSender sender = new CapturingSender(Encoding.PROTO3, Integer.MAX_VALUE);

        try (AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .build()) {
            reporter.report(FIRST_SPAN);
            reporter.flush();

            assertThat(sender.sentBatches()).containsExactly(List.of(FIRST_SPAN));
            assertThat(sender.rawMessages()).hasSize(1);
            assertThat(sender.rawMessages().get(0)).hasSize(1);
            assertThat(sender.rawMessages().get(0).get(0)).containsExactly(SpanBytesEncoder.PROTO3.encode(FIRST_SPAN));
        }
    }

    @Test
    void asyncReporterBuildWithCustomEncoderReportsJsonPayloads() {
        CapturingSender sender = new CapturingSender(Encoding.JSON, Integer.MAX_VALUE);
        JsonMessage firstMessage = new JsonMessage("first", 1);
        JsonMessage secondMessage = new JsonMessage("second", 2);
        byte[] firstEncoded = JsonMessageEncoder.INSTANCE.encode(firstMessage);
        byte[] secondEncoded = JsonMessageEncoder.INSTANCE.encode(secondMessage);

        try (AsyncReporter<JsonMessage> reporter = AsyncReporter.builder(sender)
                .messageTimeout(0, TimeUnit.MILLISECONDS)
                .build(JsonMessageEncoder.INSTANCE)) {
            reporter.report(firstMessage);
            reporter.report(secondMessage);
            reporter.flush();

            assertThat(sender.rawMessages()).hasSize(1);
            assertThat(sender.rawMessages().get(0)).hasSize(2);
            assertThat(sender.rawMessages().get(0).get(0)).containsExactly(firstEncoded);
            assertThat(sender.rawMessages().get(0).get(1)).containsExactly(secondEncoded);
        }
    }

    @Test
    void asyncReporterFlushesReportedSpansAutomaticallyUsingTheConfiguredThreadFactory() throws InterruptedException {
        CountDownLatch sendCompleted = new CountDownLatch(1);
        AtomicInteger createdThreads = new AtomicInteger();
        CapturingSender sender = new CapturingSender(Encoding.JSON, Integer.MAX_VALUE, encodedSpans -> sendCompleted.countDown());
        ThreadFactory threadFactory = runnable -> {
            createdThreads.incrementAndGet();
            return new Thread(runnable);
        };

        try (AsyncReporter<Span> reporter = AsyncReporter.builder(sender)
                .threadFactory(threadFactory)
                .messageTimeout(25, TimeUnit.MILLISECONDS)
                .build()) {
            reporter.report(FIRST_SPAN);

            assertThat(sendCompleted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(sender.sentBatches()).containsExactly(List.of(FIRST_SPAN));
            assertThat(createdThreads).hasValue(1);
        }
    }

    @Test
    void asyncReporterBuildRejectsEncodersWithDifferentEncodingThanSender() {
        CapturingSender sender = new CapturingSender(Encoding.JSON, Integer.MAX_VALUE);

        assertThatThrownBy(() -> AsyncReporter.builder(sender).build(ProtoJsonMessageEncoder.INSTANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Encoder doesn't match Sender");
    }

    @Test
    void awaitableCallbackReturnsOnSuccessAndPropagatesFailures() {
        AwaitableCallback success = new AwaitableCallback();
        success.onSuccess(null);

        assertThatCode(success::await).doesNotThrowAnyException();

        AwaitableCallback runtimeFailure = new AwaitableCallback();
        runtimeFailure.onError(new IllegalStateException("boom"));

        assertThatThrownBy(runtimeFailure::await)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        AwaitableCallback checkedFailure = new AwaitableCallback();
        checkedFailure.onError(new IOException("io failure"));

        assertThatThrownBy(checkedFailure::await)
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void bytesMessageEncoderAndInMemoryMetricsExposeExpectedPublicBehavior() {
        byte[] firstJson = SpanBytesEncoder.JSON_V2.encode(FIRST_SPAN);
        byte[] secondJson = SpanBytesEncoder.JSON_V2.encode(SECOND_SPAN);
        byte[] firstProto = SpanBytesEncoder.PROTO3.encode(FIRST_SPAN);
        byte[] secondProto = SpanBytesEncoder.PROTO3.encode(SECOND_SPAN);

        assertThat(BytesMessageEncoder.forEncoding(Encoding.JSON)).isSameAs(BytesMessageEncoder.JSON);
        assertThat(BytesMessageEncoder.forEncoding(Encoding.PROTO3)).isSameAs(BytesMessageEncoder.PROTO3);
        assertThat(new String(BytesMessageEncoder.JSON.encode(List.of(firstJson, secondJson)), StandardCharsets.UTF_8))
                .startsWith("[")
                .contains(",")
                .endsWith("]");
        assertThat(SpanBytesDecoder.JSON_V2.decodeList(BytesMessageEncoder.JSON.encode(List.of(firstJson, secondJson))))
                .containsExactly(FIRST_SPAN, SECOND_SPAN);
        assertThat(SpanBytesDecoder.PROTO3.decodeList(BytesMessageEncoder.PROTO3.encode(List.of(firstProto, secondProto))))
                .containsExactly(FIRST_SPAN, SECOND_SPAN);

        InMemoryReporterMetrics metrics = new InMemoryReporterMetrics();
        metrics.incrementMessages();
        metrics.incrementMessages();
        metrics.incrementMessageBytes(128);
        metrics.incrementSpans(3);
        metrics.incrementSpanBytes(256);
        metrics.incrementSpansDropped(2);
        metrics.updateQueuedSpans(4);
        metrics.updateQueuedBytes(64);
        metrics.incrementMessagesDropped(new RuntimeException("first"));
        metrics.incrementMessagesDropped(new RuntimeException("second"));
        metrics.incrementMessagesDropped(new IllegalStateException("closed"));

        Map<Class<? extends Throwable>, Long> droppedByCause = new LinkedHashMap<>(metrics.messagesDroppedByCause());

        assertThat(metrics.messages()).isEqualTo(2);
        assertThat(metrics.messageBytes()).isEqualTo(128);
        assertThat(metrics.spans()).isEqualTo(3);
        assertThat(metrics.spanBytes()).isEqualTo(256);
        assertThat(metrics.spansDropped()).isEqualTo(2);
        assertThat(metrics.queuedSpans()).isEqualTo(4);
        assertThat(metrics.queuedBytes()).isEqualTo(64);
        assertThat(metrics.messagesDropped()).isEqualTo(3);
        assertThat(droppedByCause)
                .containsEntry(RuntimeException.class, 2L)
                .containsEntry(IllegalStateException.class, 1L);
    }

    private static int encodedSize(Span span) {
        return SpanBytesEncoder.JSON_V2.encode(span).length;
    }

    private static Span span(String traceId, String spanId, String name, String route) {
        return Span.newBuilder()
                .traceId(traceId)
                .id(spanId)
                .name(name)
                .kind(Span.Kind.CLIENT)
                .timestamp(1_000_000L)
                .duration(150_000L)
                .putTag("http.method", "GET")
                .putTag("http.route", route)
                .build();
    }

    private static final class JsonMessage {

        private final String name;
        private final int sequence;

        private JsonMessage(String name, int sequence) {
            this.name = name;
            this.sequence = sequence;
        }

        private byte[] jsonBytes() {
            return ("{\"name\":\"" + name + "\",\"sequence\":" + sequence + "}")
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    private enum JsonMessageEncoder implements BytesEncoder<JsonMessage> {
        INSTANCE;

        @Override
        public Encoding encoding() {
            return Encoding.JSON;
        }

        @Override
        public int sizeInBytes(JsonMessage message) {
            return encode(message).length;
        }

        @Override
        public byte[] encode(JsonMessage message) {
            return message.jsonBytes();
        }

        @Override
        public byte[] encodeList(List<JsonMessage> messages) {
            List<byte[]> encodedMessages = new ArrayList<>(messages.size());
            for (JsonMessage message : messages) {
                encodedMessages.add(encode(message));
            }
            return BytesMessageEncoder.JSON.encode(encodedMessages);
        }
    }

    private enum ProtoJsonMessageEncoder implements BytesEncoder<JsonMessage> {
        INSTANCE;

        @Override
        public Encoding encoding() {
            return Encoding.PROTO3;
        }

        @Override
        public int sizeInBytes(JsonMessage message) {
            return JsonMessageEncoder.INSTANCE.sizeInBytes(message);
        }

        @Override
        public byte[] encode(JsonMessage message) {
            return JsonMessageEncoder.INSTANCE.encode(message);
        }

        @Override
        public byte[] encodeList(List<JsonMessage> messages) {
            return JsonMessageEncoder.INSTANCE.encodeList(messages);
        }
    }

    private static final class CapturingSender extends Sender {

        private final Encoding encoding;
        private final int messageMaxBytes;
        private final List<List<byte[]>> rawMessages = new ArrayList<>();
        private final Consumer<List<byte[]>> onSend;
        private boolean closed;

        private CapturingSender(Encoding encoding, int messageMaxBytes) {
            this(encoding, messageMaxBytes, encodedSpans -> {
            });
        }

        private CapturingSender(Encoding encoding, int messageMaxBytes, Consumer<List<byte[]>> onSend) {
            this.encoding = encoding;
            this.messageMaxBytes = messageMaxBytes;
            this.onSend = onSend;
        }

        @Override
        public Encoding encoding() {
            return encoding;
        }

        @Override
        public int messageMaxBytes() {
            return messageMaxBytes;
        }

        @Override
        public int messageSizeInBytes(List<byte[]> encodedSpans) {
            return encoding.listSizeInBytes(encodedSpans);
        }

        @Override
        public int messageSizeInBytes(int encodedSizeInBytes) {
            return encoding.listSizeInBytes(encodedSizeInBytes);
        }

        @Override
        public Call<Void> sendSpans(List<byte[]> encodedSpans) {
            if (closed) {
                throw new IllegalStateException("closed");
            }
            List<byte[]> copiedMessage = new ArrayList<>(encodedSpans.size());
            for (byte[] encodedSpan : encodedSpans) {
                copiedMessage.add(encodedSpan.clone());
            }
            rawMessages.add(copiedMessage);
            onSend.accept(copiedMessage);
            return Call.create(null);
        }

        @Override
        public CheckResult check() {
            return CheckResult.OK;
        }

        @Override
        public void close() {
            closed = true;
        }

        private List<List<Span>> sentBatches() {
            List<List<Span>> decodedBatches = new ArrayList<>(rawMessages.size());
            for (List<byte[]> encodedBatch : rawMessages) {
                List<Span> decodedBatch = new ArrayList<>(encodedBatch.size());
                for (byte[] encodedSpan : encodedBatch) {
                    decodedBatch.add(decoder().decodeOne(encodedSpan));
                }
                decodedBatches.add(decodedBatch);
            }
            return decodedBatches;
        }

        private List<List<byte[]>> rawMessages() {
            return rawMessages;
        }

        private SpanBytesDecoder decoder() {
            if (encoding == Encoding.JSON) {
                return SpanBytesDecoder.JSON_V2;
            }
            if (encoding == Encoding.PROTO3) {
                return SpanBytesDecoder.PROTO3;
            }
            throw new IllegalStateException("Unsupported encoding for test sender: " + encoding);
        }

        @Override
        public String toString() {
            return "CapturingSender{" + encoding + '}';
        }
    }
}
