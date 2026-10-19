/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.View;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
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

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the OTLP/HTTP push exporters: an `OtlpHttpSpanExporter` and an
 * `OtlpHttpMetricExporter` posting protobuf-encoded trace and metric export requests to an
 * OpenTelemetry collector over HTTP.
 *
 * A warehouse picking service picks an order inside a server span, reserves the stock in an
 * internal child span linked to the replenishment trace that refilled the shelf, and calls the
 * label printer in a client child span that fails. Spans leave through a flushed batch processor
 * with gzip compression and an API-key header; a local collector decodes the protobuf request and
 * sees the resource, the instrumentation scope, ids, parent ids, kinds, nanosecond timings,
 * attributes, events, links and status codes. The service's metrics leave through a periodic
 * reader that the service flushes, with delta temporality and a view that sets explicit histogram
 * buckets, so two flushes carry only what was recorded between them. A collector that answers
 * with a server error fails the export, and the exporter's own metrics count the outcome.
 */
class PickingOtlpHttpExportTest {

    private static final String TRACES_PATH = "/v1/traces";

    private static final String METRICS_PATH = "/v1/metrics";

    private static final String API_KEY = "warehouse-7f3a";

    private static final String SCOPE_NAME = "warehouse.picking";

    private static final String SCOPE_VERSION = "2.4.0";

    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");

    private static final AttributeKey<String> ENVIRONMENT = AttributeKey.stringKey("deployment.environment");

    private static final AttributeKey<String> ORDER_ID = AttributeKey.stringKey("order.id");

    private static final AttributeKey<Long> LINE_COUNT = AttributeKey.longKey("order.lines");

    private static final AttributeKey<Boolean> PRIORITY = AttributeKey.booleanKey("order.priority");

    private static final AttributeKey<Double> WEIGHT = AttributeKey.doubleKey("order.weight_kg");

    private static final AttributeKey<List<String>> AISLES = AttributeKey.stringArrayKey("order.aisles");

    private static final AttributeKey<String> ZONE = AttributeKey.stringKey("zone");

    private static final Instant PICK_START = Instant.parse("2026-05-02T06:30:00Z");

    private static final int SPAN_KIND_INTERNAL = 1;

    private static final int SPAN_KIND_SERVER = 2;

    private static final int SPAN_KIND_CLIENT = 3;

    private static final int STATUS_CODE_OK = 1;

    private static final int STATUS_CODE_ERROR = 2;

    private static final int TEMPORALITY_DELTA = 1;

    private final OtlpCollector collector = new OtlpCollector();

    private InMemoryMetricReader exporterMetrics;

    private SdkMeterProvider exporterMeterProvider;

    @BeforeEach
    void setUp() throws IOException {
        collector.start();
        exporterMetrics = InMemoryMetricReader.create();
        exporterMeterProvider = SdkMeterProvider.builder().registerMetricReader(exporterMetrics).build();
    }

    @AfterEach
    void tearDown() {
        collector.stop();
        assertThat(exporterMeterProvider.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void flushedPickingTraceReachesTheCollectorAsProtobuf() throws Exception {
        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(collector.endpoint(TRACES_PATH))
                .addHeader("api-key", API_KEY)
                .setCompression("gzip")
                .setTimeout(10, TimeUnit.SECONDS)
                .setMeterProvider(exporterMeterProvider)
                .build();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(serviceResource())
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).setScheduleDelay(1, TimeUnit.HOURS).build())
                .build();
        PickingService service = new PickingService(tracerProvider.get(SCOPE_NAME, SCOPE_VERSION), null);

        PickResult result = service.pick("order-5512", List.of("A3", "C7"));

        assertThat(result.label()).isEqualTo("order-5512@A3,C7");
        assertThat(collector.received()).isEmpty();
        assertThat(tracerProvider.forceFlush().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
        CollectorRequest request = collector.next();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo(TRACES_PATH);
        assertThat(request.contentType()).isEqualTo("application/x-protobuf");
        assertThat(request.contentEncoding()).isEqualTo("gzip");
        assertThat(request.apiKey()).isEqualTo(API_KEY);

        // ExportTraceServiceRequest: resource_spans = 1; ResourceSpans: resource = 1, scope_spans = 2.
        List<ProtoMessage> resourceSpans = ProtoMessage.parse(request.body()).messages(1);
        assertThat(resourceSpans).hasSize(1);
        assertThat(resourceSpans.get(0).message(1).attributes(1))
                .containsEntry("service.name", "picking-service")
                .containsEntry("deployment.environment", "production")
                .containsEntry("telemetry.sdk.language", "java")
                .containsEntry("telemetry.sdk.name", "opentelemetry");
        List<ProtoMessage> scopeSpans = resourceSpans.get(0).messages(2);
        assertThat(scopeSpans).hasSize(1);
        ProtoMessage scope = scopeSpans.get(0).message(1);
        assertThat(scope.string(1)).isEqualTo(SCOPE_NAME);
        assertThat(scope.string(2)).isEqualTo(SCOPE_VERSION);

        // Span: trace_id = 1, span_id = 2, parent_span_id = 4, name = 5, kind = 6, start = 7, end = 8,
        // attributes = 9, events = 11, links = 13, status = 15.
        List<ProtoMessage> spans = scopeSpans.get(0).messages(2);
        assertThat(spans).extracting(span -> span.string(5))
                .containsExactly("replenish shelf", "reserve stock", "print label", "pick order");
        Map<String, ProtoMessage> byName = spans.stream()
                .collect(Collectors.toMap(span -> span.string(5), Function.identity()));

        ProtoMessage pick = byName.get("pick order");
        assertThat(hex(pick.bytes(1))).isEqualTo(result.context().getTraceId());
        assertThat(hex(pick.bytes(2))).isEqualTo(result.context().getSpanId());
        assertThat(pick.has(4)).isFalse();
        assertThat(pick.number(6)).isEqualTo(SPAN_KIND_SERVER);
        assertThat(pick.number(7)).isEqualTo(nanos(PICK_START));
        assertThat(pick.number(8)).isEqualTo(nanos(PICK_START.plusMillis(900)));
        assertThat(pick.attributes(9))
                .containsEntry("order.id", "order-5512")
                .containsEntry("order.lines", 2L)
                .containsEntry("order.priority", true)
                .containsEntry("order.weight_kg", 12.5)
                .containsEntry("order.aisles", List.of("A3", "C7"));
        List<ProtoMessage> events = pick.messages(11);
        assertThat(events).extracting(event -> event.string(2)).containsExactly("stock reserved", "label retry");
        assertThat(events.get(0).number(1)).isEqualTo(nanos(PICK_START.plusMillis(300)));
        assertThat(events.get(1).attributes(3)).containsEntry("zone", "dock-2");
        assertThat(pick.message(15).number(3)).isEqualTo(STATUS_CODE_ERROR);
        assertThat(pick.message(15).string(2)).isEqualTo("label not printed");

        ProtoMessage reserve = byName.get("reserve stock");
        assertThat(reserve.bytes(1)).isEqualTo(pick.bytes(1));
        assertThat(reserve.bytes(4)).isEqualTo(pick.bytes(2));
        assertThat(reserve.number(6)).isEqualTo(SPAN_KIND_INTERNAL);
        assertThat(reserve.message(15).number(3)).isEqualTo(STATUS_CODE_OK);
        List<ProtoMessage> links = reserve.messages(13);
        assertThat(links).hasSize(1);
        assertThat(hex(links.get(0).bytes(1))).isEqualTo(result.replenishment().getTraceId());
        assertThat(hex(links.get(0).bytes(2))).isEqualTo(result.replenishment().getSpanId());
        assertThat(links.get(0).attributes(4)).containsEntry("zone", "aisle-A3");

        ProtoMessage print = byName.get("print label");
        assertThat(print.bytes(4)).isEqualTo(pick.bytes(2));
        assertThat(print.number(6)).isEqualTo(SPAN_KIND_CLIENT);
        assertThat(print.message(15).number(3)).isEqualTo(STATUS_CODE_ERROR);
        ProtoMessage exception = print.messages(11).get(0);
        assertThat(exception.string(2)).isEqualTo("exception");
        assertThat(exception.attributes(3))
                .containsEntry("exception.type", "java.lang.IllegalStateException")
                .containsEntry("exception.message", "printer P-12 is out of labels");

        assertThat(tracerProvider.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(exporterCount("seen", null)).isEqualTo(4L);
        assertThat(exporterCount("exported", true)).isEqualTo(4L);
    }

    @Test
    void flushedMetricsReachTheCollectorAsDeltaProtobuf() throws Exception {
        OtlpHttpMetricExporter exporter = OtlpHttpMetricExporter.builder()
                .setEndpoint(collector.endpoint(METRICS_PATH))
                .addHeader("api-key", API_KEY)
                .setAggregationTemporalitySelector(AggregationTemporalitySelector.deltaPreferred())
                .setTimeout(10, TimeUnit.SECONDS)
                .build();
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(serviceResource())
                .registerMetricReader(PeriodicMetricReader.builder(exporter).setInterval(1, TimeUnit.HOURS).build())
                .registerView(InstrumentSelector.builder().setName("pick.duration").build(), View.builder()
                        .setAggregation(Aggregation.explicitBucketHistogram(List.of(100.0, 500.0)))
                        .build())
                .build();
        PickingService service = new PickingService(TracerProvider.noop().get(SCOPE_NAME),
                meterProvider.get(SCOPE_NAME));

        service.recordPick("north", 3, 50.0);
        service.recordPick("north", 2, 120.0);
        service.recordPick("south", 4, 700.0);
        assertThat(meterProvider.forceFlush().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();

        CollectorRequest first = collector.next();
        assertThat(first.method()).isEqualTo("POST");
        assertThat(first.path()).isEqualTo(METRICS_PATH);
        assertThat(first.contentType()).isEqualTo("application/x-protobuf");
        assertThat(first.contentEncoding()).isNull();
        assertThat(first.apiKey()).isEqualTo(API_KEY);
        Map<String, ProtoMessage> metrics = metricsByName(first.body());
        assertThat(metrics).containsOnlyKeys("picks.lines", "pick.duration");

        // Metric: name = 1, description = 2, unit = 3, sum = 7, histogram = 9.
        ProtoMessage lines = metrics.get("picks.lines");
        assertThat(lines.string(2)).isEqualTo("Order lines picked");
        assertThat(lines.string(3)).isEqualTo("{line}");
        ProtoMessage sum = lines.message(7);
        assertThat(sum.number(2)).isEqualTo(TEMPORALITY_DELTA);
        assertThat(sum.number(3)).isEqualTo(1L);
        assertThat(linesByZone(sum)).containsExactlyInAnyOrderEntriesOf(Map.of("north", 5L, "south", 4L));

        // HistogramDataPoint: count = 4, sum = 5, bucket_counts = 6, explicit_bounds = 7, attributes = 9.
        ProtoMessage duration = metrics.get("pick.duration");
        assertThat(duration.string(3)).isEqualTo("ms");
        ProtoMessage histogram = duration.message(9);
        assertThat(histogram.number(2)).isEqualTo(TEMPORALITY_DELTA);
        Map<Object, ProtoMessage> durationsByZone = histogram.messages(1).stream()
                .collect(Collectors.toMap(point -> point.attributes(9).get("zone"), Function.identity()));
        ProtoMessage north = durationsByZone.get("north");
        assertThat(north.number(4)).isEqualTo(2L);
        assertThat(north.decimal(5)).isEqualTo(170.0);
        assertThat(north.packedFixed64(6)).containsExactly(1L, 1L, 0L);
        assertThat(north.packedFixed64(7)).extracting(Double::longBitsToDouble).containsExactly(100.0, 500.0);
        assertThat(durationsByZone.get("south").packedFixed64(6)).containsExactly(0L, 0L, 1L);

        service.recordPick("north", 6, 90.0);
        assertThat(meterProvider.forceFlush().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();

        Map<String, ProtoMessage> second = metricsByName(collector.next().body());
        assertThat(linesByZone(second.get("picks.lines").message(7)))
                .containsExactlyInAnyOrderEntriesOf(Map.of("north", 6L));
        List<ProtoMessage> secondDurations = second.get("pick.duration").message(9).messages(1);
        assertThat(secondDurations).hasSize(1);
        assertThat(secondDurations.get(0).number(4)).isEqualTo(1L);
        assertThat(secondDurations.get(0).packedFixed64(6)).containsExactly(1L, 0L, 0L);

        assertThat(meterProvider.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void failingCollectorFailsTheExport() throws Exception {
        InMemorySpanExporter recorder = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(serviceResource())
                .addSpanProcessor(SimpleSpanProcessor.create(recorder))
                .build();
        new PickingService(tracerProvider.get(SCOPE_NAME, SCOPE_VERSION), null).pick("order-5513", List.of("B1"));
        List<SpanData> finished = recorder.getFinishedSpanItems();
        assertThat(finished).hasSize(4);

        collector.respondWith(500);
        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(collector.endpoint(TRACES_PATH))
                .setTimeout(10, TimeUnit.SECONDS)
                .setMeterProvider(exporterMeterProvider)
                .build();
        CompletableResultCode result = exporter.export(finished).join(10, TimeUnit.SECONDS);

        assertThat(result.isDone()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        CollectorRequest request = collector.next();
        assertThat(request.apiKey()).isNull();
        List<ProtoMessage> spans = ProtoMessage.parse(request.body()).message(1).message(2).messages(2);
        assertThat(spans).extracting(span -> span.string(5))
                .containsExactlyInAnyOrder("replenish shelf", "reserve stock", "print label", "pick order");
        assertThat(exporterCount("seen", null)).isEqualTo(4L);
        assertThat(exporterCount("exported", false)).isEqualTo(4L);
        assertThat(exporter.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(tracerProvider.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    private static Resource serviceResource() {
        return Resource.getDefault().merge(Resource.create(
                Attributes.of(SERVICE_NAME, "picking-service", ENVIRONMENT, "production")));
    }

    /// Indexes the metrics of a decoded `ExportMetricsServiceRequest` by name.
    private static Map<String, ProtoMessage> metricsByName(byte[] body) {
        // ResourceMetrics: scope_metrics = 2; ScopeMetrics: metrics = 2.
        List<ProtoMessage> resourceMetrics = ProtoMessage.parse(body).messages(1);
        assertThat(resourceMetrics).hasSize(1);
        assertThat(resourceMetrics.get(0).message(1).attributes(1)).containsEntry("service.name", "picking-service");
        ProtoMessage scopeMetrics = resourceMetrics.get(0).message(2);
        assertThat(scopeMetrics.message(1).string(1)).isEqualTo(SCOPE_NAME);
        return scopeMetrics.messages(2).stream()
                .collect(Collectors.toMap(metric -> metric.string(1), Function.identity()));
    }

    /// Maps the `zone` attribute of each `NumberDataPoint` (attributes = 7, as_int = 6) to its value.
    private static Map<Object, Long> linesByZone(ProtoMessage sum) {
        return sum.messages(1).stream()
                .collect(Collectors.toMap(point -> point.attributes(7).get("zone"), point -> point.number(6)));
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    private static long nanos(Instant instant) {
        return TimeUnit.SECONDS.toNanos(instant.getEpochSecond()) + instant.getNano();
    }

    /// Sums the exporter's own `seen` or `exported` counter, filtered by the `success` attribute.
    private long exporterCount(String counter, Boolean success) {
        return exporterMetrics.collectAllMetrics().stream()
                .filter(metric -> metric.getName().endsWith(".exporter." + counter))
                .map(MetricData::getLongSumData)
                .flatMap(data -> data.getPoints().stream())
                .filter(point -> success == null
                        || success.equals(point.getAttributes().get(AttributeKey.booleanKey("success"))))
                .mapToLong(LongPointData::getValue)
                .sum();
    }

    /** An export request the collector received. */
    record CollectorRequest(String method, String path, String contentType, String contentEncoding,
            String apiKey, byte[] body) {
    }

    /** Local OTLP/HTTP collector that accepts export posts, inflating gzip bodies, and queues them. */
    static final class OtlpCollector {

        private final BlockingQueue<CollectorRequest> requests = new LinkedBlockingQueue<>();

        private volatile int status = 200;

        private HttpServer server;

        void start() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext(TRACES_PATH, this::handle);
            server.createContext(METRICS_PATH, this::handle);
            server.start();
        }

        void stop() {
            server.stop(0);
        }

        String endpoint(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + path;
        }

        void respondWith(int newStatus) {
            status = newStatus;
        }

        List<CollectorRequest> received() {
            return List.copyOf(requests);
        }

        CollectorRequest next() throws InterruptedException {
            CollectorRequest request = requests.poll(10, TimeUnit.SECONDS);
            assertThat(request).as("export request received by the collector").isNotNull();
            return request;
        }

        private void handle(HttpExchange exchange) throws IOException {
            String encoding = exchange.getRequestHeaders().getFirst("Content-Encoding");
            byte[] body;
            try (InputStream in = "gzip".equals(encoding)
                    ? new GZIPInputStream(exchange.getRequestBody())
                    : exchange.getRequestBody()) {
                body = in.readAllBytes();
            }
            requests.add(new CollectorRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Content-Type"), encoding,
                    exchange.getRequestHeaders().getFirst("api-key"), body));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
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

        double decimal(int field) {
            return Double.longBitsToDouble(number(field));
        }

        /// Reads a repeated 64-bit field, whether the writer packed it or not.
        List<Long> packedFixed64(int field) {
            List<Long> values = new ArrayList<>();
            for (Object value : fields.getOrDefault(field, List.of())) {
                if (value instanceof byte[] packed) {
                    ByteBuffer buffer = ByteBuffer.wrap(packed).order(ByteOrder.LITTLE_ENDIAN);
                    while (buffer.hasRemaining()) {
                        values.add(buffer.getLong());
                    }
                } else {
                    values.add((Long) value);
                }
            }
            return values;
        }

        /// Decodes repeated `KeyValue` messages (key = 1, value = 2) into a map of plain values.
        Map<String, Object> attributes(int field) {
            Map<String, Object> attributes = new LinkedHashMap<>();
            for (ProtoMessage keyValue : messages(field)) {
                attributes.put(keyValue.string(1), keyValue.message(2).anyValue());
            }
            return attributes;
        }

        /// Decodes an `AnyValue`: string = 1, bool = 2, int = 3, double = 4, array = 5.
        private Object anyValue() {
            if (has(1)) {
                return string(1);
            }
            if (has(2)) {
                return number(2) != 0;
            }
            if (has(3)) {
                return number(3);
            }
            if (has(4)) {
                return decimal(4);
            }
            return message(5).messages(1).stream().map(ProtoMessage::anyValue).toList();
        }
    }

    /** What a pick returns: the printed label and the span contexts an operator would log. */
    record PickResult(String label, SpanContext context, SpanContext replenishment) {
    }

    /** Picking service instrumented with the OpenTelemetry tracing and metrics API. */
    static final class PickingService {

        private final Tracer tracer;

        private final LongCounter pickedLines;

        private final DoubleHistogram pickDuration;

        PickingService(Tracer tracer, Meter meter) {
            this.tracer = tracer;
            this.pickedLines = meter == null ? null : meter.counterBuilder("picks.lines")
                    .setDescription("Order lines picked")
                    .setUnit("{line}")
                    .build();
            this.pickDuration = meter == null ? null : meter.histogramBuilder("pick.duration")
                    .setDescription("Time to pick an order")
                    .setUnit("ms")
                    .build();
        }

        void recordPick(String zone, long lines, double millis) {
            Attributes attributes = Attributes.of(ZONE, zone);
            pickedLines.add(lines, attributes);
            pickDuration.record(millis, attributes);
        }

        PickResult pick(String orderId, List<String> aisles) {
            Span replenishment = tracer.spanBuilder("replenish shelf")
                    .setNoParent()
                    .setStartTimestamp(PICK_START.minusSeconds(3600))
                    .startSpan();
            replenishment.end(PICK_START.minusSeconds(3000));
            Span span = tracer.spanBuilder("pick order")
                    .setSpanKind(SpanKind.SERVER)
                    .setStartTimestamp(PICK_START)
                    .setAttribute(ORDER_ID, orderId)
                    .setAttribute(LINE_COUNT, (long) aisles.size())
                    .setAttribute(PRIORITY, true)
                    .setAttribute(WEIGHT, 12.5)
                    .setAttribute(AISLES, aisles)
                    .startSpan();
            try (Scope ignored = span.makeCurrent()) {
                reserveStock(replenishment.getSpanContext(), aisles.get(0));
                span.addEvent("stock reserved", PICK_START.plusMillis(300));
                try {
                    printLabel();
                } catch (IllegalStateException e) {
                    span.addEvent("label retry", Attributes.of(ZONE, "dock-2"), PICK_START.plusMillis(800));
                    span.setStatus(StatusCode.ERROR, "label not printed");
                }
                return new PickResult(orderId + "@" + String.join(",", aisles), span.getSpanContext(),
                        replenishment.getSpanContext());
            } finally {
                span.end(PICK_START.plusMillis(900));
            }
        }

        private void reserveStock(SpanContext replenishment, String aisle) {
            Span span = tracer.spanBuilder("reserve stock")
                    .addLink(replenishment, Attributes.of(ZONE, "aisle-" + aisle))
                    .setStartTimestamp(PICK_START.plusMillis(50))
                    .startSpan();
            span.setStatus(StatusCode.OK);
            span.end(PICK_START.plusMillis(300));
        }

        private void printLabel() {
            Span span = tracer.spanBuilder("print label")
                    .setSpanKind(SpanKind.CLIENT)
                    .setStartTimestamp(PICK_START.plusMillis(350))
                    .startSpan();
            try {
                throw new IllegalStateException("printer P-12 is out of labels");
            } catch (IllegalStateException e) {
                span.recordException(e);
                span.setStatus(StatusCode.ERROR);
                throw e;
            } finally {
                span.end(PICK_START.plusMillis(750));
            }
        }
    }
}
