/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the OTLP/gRPC push exporters: an `OtlpGrpcSpanExporter` and an
 * `OtlpGrpcMetricExporter` sending length-prefixed protobuf export messages to an OpenTelemetry
 * collector over cleartext HTTP/2.
 *
 * A remittance service sends a transfer inside a server span, screens the sender in an internal
 * child span and settles with the partner bank in a client child span. Its spans leave through a
 * flushed batch processor with gzip compression; a local collector speaking HTTP/2 with prior
 * knowledge reads the gRPC message frame, inflates it, decodes the trace export request and
 * answers with an OK gRPC status in the trailers. The service's transfer counter and queue gauge
 * leave through a flushed periodic reader as cumulative sums and gauges, uncompressed. A collector
 * that answers with the UNAVAILABLE gRPC status fails the export, and the exporter's own metrics
 * count the spans it saw, delivered and lost.
 */
class RemittanceOtlpGrpcExportTest {

    private static final String SCOPE_NAME = "payments.remittance";

    private static final String SCOPE_VERSION = "3.1.0";

    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");

    private static final AttributeKey<String> REGION = AttributeKey.stringKey("cloud.region");

    private static final AttributeKey<String> TRANSFER_ID = AttributeKey.stringKey("transfer.id");

    private static final AttributeKey<Long> AMOUNT_CENTS = AttributeKey.longKey("transfer.amount_cents");

    private static final AttributeKey<String> CORRIDOR = AttributeKey.stringKey("corridor");

    private static final AttributeKey<String> PARTNER = AttributeKey.stringKey("partner.bank");

    private static final int SPAN_KIND_INTERNAL = 1;

    private static final int SPAN_KIND_SERVER = 2;

    private static final int SPAN_KIND_CLIENT = 3;

    private static final int STATUS_CODE_OK = 1;

    private static final int STATUS_CODE_ERROR = 2;

    private static final int TEMPORALITY_CUMULATIVE = 2;

    private static final String GRPC_UNAVAILABLE = "14";

    private final GrpcCollector collector = new GrpcCollector();

    private InMemoryMetricReader exporterMetrics;

    private SdkMeterProvider exporterMeterProvider;

    @BeforeEach
    void setUp() throws IOException {
        collector.start();
        exporterMetrics = InMemoryMetricReader.create();
        exporterMeterProvider = SdkMeterProvider.builder().registerMetricReader(exporterMetrics).build();
    }

    @AfterEach
    void tearDown() throws IOException {
        collector.stop();
        assertThat(exporterMeterProvider.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void flushedTransferTraceReachesTheCollectorAsCompressedGrpcMessage() throws Exception {
        OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(collector.endpoint())
                .setCompression("gzip")
                .setTimeout(10, TimeUnit.SECONDS)
                .setMeterProvider(exporterMeterProvider)
                .build();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(serviceResource())
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).setScheduleDelay(1, TimeUnit.HOURS).build())
                .build();
        RemittanceService service = new RemittanceService(tracerProvider.get(SCOPE_NAME, SCOPE_VERSION), null);

        SpanContext transfer = service.send("tr-90210", 125_000L, "EU-PH");

        assertThat(collector.received()).isEmpty();
        assertThat(tracerProvider.forceFlush().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
        GrpcMessage message = collector.next();
        assertThat(message.compressed()).isTrue();

        // ExportTraceServiceRequest: resource_spans = 1; ResourceSpans: resource = 1, scope_spans = 2.
        List<ProtoMessage> resourceSpans = ProtoMessage.parse(message.payload()).messages(1);
        assertThat(resourceSpans).hasSize(1);
        assertThat(resourceSpans.get(0).message(1).attributes(1))
                .containsEntry("service.name", "remittance-service")
                .containsEntry("cloud.region", "eu-west-1")
                .containsEntry("telemetry.sdk.language", "java");
        ProtoMessage scopeSpans = resourceSpans.get(0).message(2);
        assertThat(scopeSpans.message(1).string(1)).isEqualTo(SCOPE_NAME);
        assertThat(scopeSpans.message(1).string(2)).isEqualTo(SCOPE_VERSION);

        // Span: trace_id = 1, span_id = 2, parent_span_id = 4, name = 5, kind = 6, attributes = 9,
        // events = 11, status = 15.
        Map<String, ProtoMessage> spans = scopeSpans.messages(2).stream()
                .collect(Collectors.toMap(span -> span.string(5), Function.identity()));
        assertThat(spans).containsOnlyKeys("send transfer", "screen sender", "settle with partner");

        ProtoMessage send = spans.get("send transfer");
        assertThat(hex(send.bytes(1))).isEqualTo(transfer.getTraceId());
        assertThat(hex(send.bytes(2))).isEqualTo(transfer.getSpanId());
        assertThat(send.has(4)).isFalse();
        assertThat(send.number(6)).isEqualTo(SPAN_KIND_SERVER);
        assertThat(send.attributes(9))
                .containsEntry("transfer.id", "tr-90210")
                .containsEntry("transfer.amount_cents", 125_000L)
                .containsEntry("corridor", "EU-PH");
        assertThat(send.messages(11)).extracting(event -> event.string(2)).containsExactly("funds reserved");
        assertThat(send.message(15).number(3)).isEqualTo(STATUS_CODE_OK);

        ProtoMessage screen = spans.get("screen sender");
        assertThat(screen.bytes(1)).isEqualTo(send.bytes(1));
        assertThat(screen.bytes(4)).isEqualTo(send.bytes(2));
        assertThat(screen.number(6)).isEqualTo(SPAN_KIND_INTERNAL);

        ProtoMessage settle = spans.get("settle with partner");
        assertThat(settle.bytes(4)).isEqualTo(send.bytes(2));
        assertThat(settle.number(6)).isEqualTo(SPAN_KIND_CLIENT);
        assertThat(settle.attributes(9)).containsEntry("partner.bank", "BDO");
        assertThat(settle.message(15).number(3)).isEqualTo(STATUS_CODE_ERROR);
        assertThat(settle.message(15).string(2)).isEqualTo("partner timeout, retried");

        assertThat(tracerProvider.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(exporterCount("seen", null)).isEqualTo(3L);
        assertThat(exporterCount("exported", true)).isEqualTo(3L);
        assertThat(exporterCount("exported", false)).isZero();
    }

    @Test
    void flushedMetricsReachTheCollectorAsCumulativeGrpcMessages() throws Exception {
        OtlpGrpcMetricExporter exporter = OtlpGrpcMetricExporter.builder()
                .setEndpoint(collector.endpoint())
                .setTimeout(10, TimeUnit.SECONDS)
                .build();
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(serviceResource())
                .registerMetricReader(PeriodicMetricReader.builder(exporter).setInterval(1, TimeUnit.HOURS).build())
                .build();
        RemittanceService service = new RemittanceService(null, meterProvider.get(SCOPE_NAME));

        service.recordTransfer("EU-PH");
        service.recordTransfer("EU-PH");
        service.recordTransfer("US-MX");
        service.queued(7);
        assertThat(meterProvider.forceFlush().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();

        GrpcMessage first = collector.next();
        assertThat(first.compressed()).isFalse();
        Map<String, ProtoMessage> metrics = metricsByName(first.payload());
        assertThat(metrics).containsOnlyKeys("remittances.sent", "remittance.queue.depth");

        // Metric: name = 1, description = 2, unit = 3, gauge = 5, sum = 7.
        ProtoMessage sent = metrics.get("remittances.sent");
        assertThat(sent.string(2)).isEqualTo("Transfers handed to partner banks");
        assertThat(sent.string(3)).isEqualTo("{transfer}");
        ProtoMessage sum = sent.message(7);
        assertThat(sum.number(2)).isEqualTo(TEMPORALITY_CUMULATIVE);
        assertThat(sum.number(3)).isEqualTo(1L);
        assertThat(valuesByCorridor(sum)).containsExactlyInAnyOrderEntriesOf(Map.of("EU-PH", 2L, "US-MX", 1L));
        ProtoMessage depth = metrics.get("remittance.queue.depth");
        assertThat(depth.message(5).messages(1)).extracting(point -> point.number(6)).containsExactly(7L);

        service.recordTransfer("US-MX");
        service.queued(2);
        assertThat(meterProvider.forceFlush().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();

        Map<String, ProtoMessage> second = metricsByName(collector.next().payload());
        assertThat(valuesByCorridor(second.get("remittances.sent").message(7)))
                .containsExactlyInAnyOrderEntriesOf(Map.of("EU-PH", 2L, "US-MX", 2L));
        assertThat(second.get("remittance.queue.depth").message(5).messages(1))
                .extracting(point -> point.number(6)).containsExactly(2L);

        assertThat(meterProvider.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
        service.close();
    }

    @Test
    void unavailableCollectorFailsTheExport() throws Exception {
        InMemorySpanExporter recorder = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(serviceResource())
                .addSpanProcessor(SimpleSpanProcessor.create(recorder))
                .build();
        new RemittanceService(tracerProvider.get(SCOPE_NAME, SCOPE_VERSION), null).send("tr-90211", 4_000L, "US-MX");
        List<SpanData> finished = recorder.getFinishedSpanItems();
        assertThat(finished).hasSize(3);

        collector.respondWith(GRPC_UNAVAILABLE);
        OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(collector.endpoint())
                .setTimeout(10, TimeUnit.SECONDS)
                .setMeterProvider(exporterMeterProvider)
                .build();
        CompletableResultCode result = exporter.export(finished).join(10, TimeUnit.SECONDS);

        assertThat(result.isDone()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        GrpcMessage message = collector.next();
        assertThat(message.compressed()).isFalse();
        List<ProtoMessage> spans = ProtoMessage.parse(message.payload()).message(1).message(2).messages(2);
        assertThat(spans).extracting(span -> span.string(5))
                .containsExactlyInAnyOrder("send transfer", "screen sender", "settle with partner");
        assertThat(exporterCount("seen", null)).isEqualTo(3L);
        assertThat(exporterCount("exported", false)).isEqualTo(3L);
        assertThat(exporterCount("exported", true)).isZero();
        assertThat(exporter.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(tracerProvider.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    private static Resource serviceResource() {
        return Resource.getDefault().merge(Resource.create(
                Attributes.of(SERVICE_NAME, "remittance-service", REGION, "eu-west-1")));
    }

    /// Indexes the metrics of a decoded `ExportMetricsServiceRequest` by name.
    private static Map<String, ProtoMessage> metricsByName(byte[] payload) {
        // ResourceMetrics: resource = 1, scope_metrics = 2; ScopeMetrics: scope = 1, metrics = 2.
        List<ProtoMessage> resourceMetrics = ProtoMessage.parse(payload).messages(1);
        assertThat(resourceMetrics).hasSize(1);
        assertThat(resourceMetrics.get(0).message(1).attributes(1))
                .containsEntry("service.name", "remittance-service");
        ProtoMessage scopeMetrics = resourceMetrics.get(0).message(2);
        assertThat(scopeMetrics.message(1).string(1)).isEqualTo(SCOPE_NAME);
        return scopeMetrics.messages(2).stream()
                .collect(Collectors.toMap(metric -> metric.string(1), Function.identity()));
    }

    /// Maps the `corridor` attribute of each `NumberDataPoint` (attributes = 7, as_int = 6) to its value.
    private static Map<Object, Long> valuesByCorridor(ProtoMessage sum) {
        return sum.messages(1).stream()
                .collect(Collectors.toMap(point -> point.attributes(7).get("corridor"), point -> point.number(6)));
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    /// Sums the exporter's own `seen` or `exported` span counter, filtered by the `success` attribute.
    private long exporterCount(String counter, Boolean success) {
        return exporterMetrics.collectAllMetrics().stream()
                .filter(metric -> metric.getName().equals("otlp.exporter." + counter))
                .filter(metric -> metric.getInstrumentationScopeInfo().getName()
                        .equals("io.opentelemetry.exporters.otlp-grpc-okhttp"))
                .map(MetricData::getLongSumData)
                .flatMap(data -> data.getPoints().stream())
                .filter(point -> "span".equals(point.getAttributes().get(AttributeKey.stringKey("type"))))
                .filter(point -> success == null
                        || success.equals(point.getAttributes().get(AttributeKey.booleanKey("success"))))
                .mapToLong(LongPointData::getValue)
                .sum();
    }

    /** A gRPC message the collector received: its compressed flag and its inflated payload. */
    record GrpcMessage(boolean compressed, byte[] payload) {
    }

    /// Local OTLP/gRPC collector speaking cleartext HTTP/2 with prior knowledge.
    ///
    /// It reads each request stream's DATA frames, unwraps the 5-byte gRPC message prefix and
    /// answers with response headers, an empty response message and `grpc-status` trailers, or
    /// with a trailers-only error response once told to fail.
    static final class GrpcCollector {

        private static final byte[] PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

        private static final int DATA = 0x0;

        private static final int HEADERS = 0x1;

        private static final int SETTINGS = 0x4;

        private static final int PING = 0x6;

        private static final int GOAWAY = 0x7;

        private static final int END_STREAM = 0x1;

        private static final int ACK = 0x1;

        private static final int END_HEADERS = 0x4;

        private static final int PADDED = 0x8;

        private final BlockingQueue<GrpcMessage> messages = new LinkedBlockingQueue<>();

        private final List<Socket> connections = new CopyOnWriteArrayList<>();

        private volatile String grpcStatus = "0";

        private ServerSocket server;

        private Thread acceptor;

        void start() throws IOException {
            server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            acceptor = new Thread(this::accept, "otlp-grpc-collector");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        void stop() throws IOException {
            server.close();
            for (Socket connection : connections) {
                connection.close();
            }
        }

        String endpoint() {
            return "http://127.0.0.1:" + server.getLocalPort();
        }

        void respondWith(String status) {
            grpcStatus = status;
        }

        List<GrpcMessage> received() {
            return List.copyOf(messages);
        }

        GrpcMessage next() throws InterruptedException {
            GrpcMessage message = messages.poll(10, TimeUnit.SECONDS);
            assertThat(message).as("gRPC export message received by the collector").isNotNull();
            return message;
        }

        private void accept() {
            while (!server.isClosed()) {
                try {
                    Socket connection = server.accept();
                    connections.add(connection);
                    Thread reader = new Thread(() -> serve(connection), "otlp-grpc-connection");
                    reader.setDaemon(true);
                    reader.start();
                } catch (IOException closed) {
                    return;
                }
            }
        }

        private void serve(Socket connection) {
            try (connection) {
                DataInputStream in = new DataInputStream(connection.getInputStream());
                OutputStream out = connection.getOutputStream();
                byte[] preface = new byte[PREFACE.length];
                in.readFully(preface);
                assertThat(preface).isEqualTo(PREFACE);
                writeFrame(out, SETTINGS, 0, 0, new byte[0]);
                Map<Integer, ByteArrayOutputStream> bodies = new ConcurrentHashMap<>();
                while (true) {
                    int length = (in.readUnsignedByte() << 16) | in.readUnsignedShort();
                    int type = in.readUnsignedByte();
                    int flags = in.readUnsignedByte();
                    int stream = in.readInt() & 0x7FFFFFFF;
                    byte[] payload = new byte[length];
                    in.readFully(payload);
                    switch (type) {
                        case SETTINGS -> {
                            if ((flags & ACK) == 0) {
                                writeFrame(out, SETTINGS, ACK, 0, new byte[0]);
                            }
                        }
                        case PING -> {
                            if ((flags & ACK) == 0) {
                                writeFrame(out, PING, ACK, 0, payload);
                            }
                        }
                        case HEADERS -> bodies.put(stream, new ByteArrayOutputStream());
                        case DATA -> {
                            int padding = (flags & PADDED) != 0 ? payload[0] & 0xFF : 0;
                            int offset = (flags & PADDED) != 0 ? 1 : 0;
                            bodies.get(stream).write(payload, offset, payload.length - offset - padding);
                            if ((flags & END_STREAM) != 0) {
                                messages.add(unwrap(bodies.remove(stream).toByteArray()));
                                respond(out, stream);
                            }
                        }
                        case GOAWAY -> {
                            return;
                        }
                        default -> {
                            // WINDOW_UPDATE, PRIORITY and RST_STREAM need no answer from this collector.
                        }
                    }
                }
            } catch (EOFException | SocketException closed) {
                // The exporter or the test closed the connection.
            } catch (IOException e) {
                throw new IllegalStateException("collector connection failed", e);
            }
        }

        /// Strips the gRPC message prefix (compressed flag and big-endian length) and inflates gzip.
        private static GrpcMessage unwrap(byte[] body) throws IOException {
            ByteBuffer buffer = ByteBuffer.wrap(body);
            boolean compressed = buffer.get() == 1;
            byte[] message = Arrays.copyOfRange(body, 5, 5 + buffer.getInt());
            if (!compressed) {
                return new GrpcMessage(false, message);
            }
            try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(message))) {
                return new GrpcMessage(true, in.readAllBytes());
            }
        }

        private void respond(OutputStream out, int stream) throws IOException {
            String status = grpcStatus;
            ByteArrayOutputStream headers = new ByteArrayOutputStream();
            headers.write(0x88); // `:status: 200` from the HPACK static table.
            literal(headers, "content-type", "application/grpc");
            if (!"0".equals(status)) {
                literal(headers, "grpc-status", status);
                literal(headers, "grpc-message", "collector is draining");
                writeFrame(out, HEADERS, END_HEADERS | END_STREAM, stream, headers.toByteArray());
                return;
            }
            writeFrame(out, HEADERS, END_HEADERS, stream, headers.toByteArray());
            writeFrame(out, DATA, 0, stream, new byte[5]);
            ByteArrayOutputStream trailers = new ByteArrayOutputStream();
            literal(trailers, "grpc-status", status);
            writeFrame(out, HEADERS, END_HEADERS | END_STREAM, stream, trailers.toByteArray());
        }

        /// Writes an HPACK literal header field without indexing, with a new name and no Huffman coding.
        private static void literal(ByteArrayOutputStream block, String name, String value) {
            block.write(0x00);
            for (String part : List.of(name, value)) {
                byte[] bytes = part.getBytes(StandardCharsets.US_ASCII);
                block.write(bytes.length);
                block.writeBytes(bytes);
            }
        }

        private static void writeFrame(OutputStream out, int type, int flags, int stream, byte[] payload)
                throws IOException {
            ByteBuffer frame = ByteBuffer.allocate(9 + payload.length);
            frame.put((byte) (payload.length >>> 16)).putShort((short) payload.length);
            frame.put((byte) type).put((byte) flags).putInt(stream).put(payload);
            synchronized (out) {
                out.write(frame.array());
                out.flush();
            }
        }
    }

    /// A protobuf message decoded from the wire format into its fields, as the collector reads it.
    ///
    /// Varint and fixed-width fields decode to `Long`, length-delimited fields to `byte[]`.
    static final class ProtoMessage {

        private final Map<Integer, List<Object>> fields = new HashMap<>();

        static ProtoMessage parse(byte[] bytes) {
            ProtoMessage message = new ProtoMessage();
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            while (buffer.hasRemaining()) {
                long tag = readVarint(buffer);
                Object value = switch ((int) (tag & 7)) {
                    case 0 -> readVarint(buffer);
                    case 1 -> buffer.getLong();
                    case 2 -> {
                        byte[] chunk = new byte[(int) readVarint(buffer)];
                        buffer.get(chunk);
                        yield chunk;
                    }
                    case 5 -> (long) buffer.getInt();
                    default -> throw new IllegalArgumentException("unsupported wire type in tag " + tag);
                };
                message.fields.computeIfAbsent((int) (tag >>> 3), field -> new ArrayList<>()).add(value);
            }
            return message;
        }

        private static long readVarint(ByteBuffer buffer) {
            long value = 0;
            for (int shift = 0; ; shift += 7) {
                byte next = buffer.get();
                value |= (long) (next & 0x7F) << shift;
                if (next >= 0) {
                    return value;
                }
            }
        }

        boolean has(int field) {
            return fields.containsKey(field);
        }

        List<ProtoMessage> messages(int field) {
            return fields.getOrDefault(field, List.of()).stream().map(chunk -> parse((byte[]) chunk)).toList();
        }

        ProtoMessage message(int field) {
            List<ProtoMessage> messages = messages(field);
            assertThat(messages).as("message field %d", field).hasSize(1);
            return messages.get(0);
        }

        byte[] bytes(int field) {
            return (byte[]) fields.getOrDefault(field, List.of(new byte[0])).get(0);
        }

        String string(int field) {
            return new String(bytes(field), StandardCharsets.UTF_8);
        }

        long number(int field) {
            return (Long) fields.getOrDefault(field, List.of(0L)).get(0);
        }

        /// Decodes repeated `KeyValue` messages (key = 1, value = 2) into a map of plain values.
        Map<String, Object> attributes(int field) {
            Map<String, Object> attributes = new LinkedHashMap<>();
            for (ProtoMessage keyValue : messages(field)) {
                attributes.put(keyValue.string(1), keyValue.message(2).anyValue());
            }
            return attributes;
        }

        /// Decodes the string (1), bool (2) and int (3) cases of an `AnyValue`.
        private Object anyValue() {
            if (has(1)) {
                return string(1);
            }
            if (has(2)) {
                return number(2) != 0;
            }
            return number(3);
        }
    }

    /** Remittance service instrumented with the OpenTelemetry tracing and metrics API. */
    static final class RemittanceService implements AutoCloseable {

        private final Tracer tracer;

        private final LongCounter sent;

        private final AtomicLong queueDepth = new AtomicLong();

        private final ObservableLongGauge queueGauge;

        RemittanceService(Tracer tracer, Meter meter) {
            this.tracer = tracer;
            this.sent = meter == null ? null : meter.counterBuilder("remittances.sent")
                    .setDescription("Transfers handed to partner banks")
                    .setUnit("{transfer}")
                    .build();
            this.queueGauge = meter == null ? null : meter.gaugeBuilder("remittance.queue.depth")
                    .ofLongs()
                    .setDescription("Transfers waiting for a partner bank")
                    .buildWithCallback(measurement -> measurement.record(queueDepth.get()));
        }

        void recordTransfer(String corridor) {
            sent.add(1, Attributes.of(CORRIDOR, corridor));
        }

        void queued(long depth) {
            queueDepth.set(depth);
        }

        SpanContext send(String transferId, long amountCents, String corridor) {
            Span span = tracer.spanBuilder("send transfer")
                    .setSpanKind(SpanKind.SERVER)
                    .setAttribute(TRANSFER_ID, transferId)
                    .setAttribute(AMOUNT_CENTS, amountCents)
                    .setAttribute(CORRIDOR, corridor)
                    .startSpan();
            try (Scope ignored = span.makeCurrent()) {
                tracer.spanBuilder("screen sender").startSpan().end();
                span.addEvent("funds reserved");
                Span settle = tracer.spanBuilder("settle with partner")
                        .setSpanKind(SpanKind.CLIENT)
                        .setAttribute(PARTNER, "BDO")
                        .startSpan();
                settle.setStatus(StatusCode.ERROR, "partner timeout, retried");
                settle.end();
                span.setStatus(StatusCode.OK);
                return span.getSpanContext();
            } finally {
                span.end();
            }
        }

        @Override
        public void close() {
            if (queueGauge != null) {
                queueGauge.close();
            }
        }
    }
}
