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
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporterBuilder;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zipkin2.Annotation;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.codec.SpanBytesEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
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
 * Exercises the Zipkin push exporter: a `ZipkinSpanExporter` that converts finished SDK spans to
 * Zipkin v2 spans and posts them as gzipped JSON to a Zipkin collector over HTTP.
 *
 * A fleet dispatch service assigns a delivery to a driver inside a server span, looks the driver up
 * in an internal child span and notifies the driver app in a client child span, which fails. The
 * spans leave the process through a batch processor that the service flushes, and arrive at a local
 * Zipkin collector endpoint that decodes them back. The collector sees one trace with parent links,
 * the service name and address as the local endpoint, attributes and status as tags, events as
 * annotations and microsecond timings. A second deployment exports through a simple processor that
 * posts every span as it ends, and an unavailable collector makes the export fail; the exporter's
 * own metrics count both outcomes.
 */
class DispatchZipkinExportTest {

    private static final String SPANS_PATH = "/api/v2/spans";

    private static final String SCOPE_NAME = "fleet.dispatch";

    private static final String SCOPE_VERSION = "3.1.0";

    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");

    private static final AttributeKey<String> DELIVERY_ID = AttributeKey.stringKey("delivery.id");

    private static final AttributeKey<Long> PARCEL_COUNT = AttributeKey.longKey("delivery.parcels");

    private static final AttributeKey<Boolean> EXPRESS = AttributeKey.booleanKey("delivery.express");

    private static final AttributeKey<List<String>> ZONES = AttributeKey.stringArrayKey("delivery.zones");

    private static final AttributeKey<String> DRIVER_ID = AttributeKey.stringKey("driver.id");

    private static final Instant DISPATCH_START = Instant.parse("2026-03-14T08:00:00Z");

    private final ZipkinCollector collector = new ZipkinCollector();

    private InMemoryMetricReader exporterMetrics;

    private SdkMeterProvider meterProvider;

    @BeforeEach
    void setUp() throws IOException {
        collector.start();
        exporterMetrics = InMemoryMetricReader.create();
        meterProvider = SdkMeterProvider.builder().registerMetricReader(exporterMetrics).build();
    }

    @AfterEach
    void tearDown() {
        collector.stop();
        assertThat(meterProvider.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void flushedDispatchTraceReachesTheCollectorAsZipkinJson() throws Exception {
        ZipkinSpanExporter exporter = exporterBuilder().setEncoder(SpanBytesEncoder.JSON_V2).build();
        SdkTracerProvider tracerProvider = tracerProvider(BatchSpanProcessor.builder(exporter)
                .setScheduleDelay(1, TimeUnit.HOURS)
                .build());
        DispatchService service = new DispatchService(tracerProvider.get(SCOPE_NAME, SCOPE_VERSION));

        String assignment = service.dispatch("delivery-77", "driver-9", List.of("north", "harbour"));

        assertThat(assignment).isEqualTo("delivery-77->driver-9");
        assertThat(collector.received()).isEmpty();
        assertThat(tracerProvider.forceFlush().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
        CollectorRequest request = collector.next();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo(SPANS_PATH);
        assertThat(request.contentType()).isEqualTo("application/json");
        assertThat(request.contentEncoding()).isEqualTo("gzip");

        List<zipkin2.Span> spans = SpanBytesDecoder.JSON_V2.decodeList(request.body());
        assertThat(spans).extracting(zipkin2.Span::name)
                .containsExactly("find driver", "notify driver", "dispatch");
        Map<String, zipkin2.Span> byName = spans.stream()
                .collect(Collectors.toMap(zipkin2.Span::name, Function.identity()));

        zipkin2.Span dispatch = byName.get("dispatch");
        assertThat(dispatch.kind()).isEqualTo(zipkin2.Span.Kind.SERVER);
        assertThat(dispatch.parentId()).isNull();
        assertThat(dispatch.localServiceName()).isEqualTo("dispatch-service");
        assertThat(dispatch.localEndpoint().ipv4()).isEqualTo("127.0.0.1");
        assertThat(dispatch.timestampAsLong()).isEqualTo(DISPATCH_START.toEpochMilli() * 1000);
        assertThat(dispatch.durationAsLong()).isEqualTo(1_500_000L);
        assertThat(dispatch.tags())
                .containsEntry("delivery.id", "delivery-77")
                .containsEntry("delivery.parcels", "3")
                .containsEntry("delivery.express", "true")
                .containsEntry("delivery.zones", "north,harbour")
                .containsEntry("otel.status_code", "ERROR")
                .containsEntry("error", "driver unreachable")
                .containsEntry("otel.scope.name", SCOPE_NAME)
                .containsEntry("otel.scope.version", SCOPE_VERSION)
                .containsEntry("otel.library.name", SCOPE_NAME)
                .containsEntry("otel.library.version", SCOPE_VERSION);
        assertThat(dispatch.annotations()).extracting(Annotation::value)
                .containsExactly("driver assigned", "notification failed");
        assertThat(dispatch.annotations()).extracting(Annotation::timestamp).containsExactly(
                DISPATCH_START.plusMillis(400).toEpochMilli() * 1000,
                DISPATCH_START.plusMillis(1200).toEpochMilli() * 1000);

        zipkin2.Span find = byName.get("find driver");
        assertThat(find.kind()).isNull();
        assertThat(find.traceId()).isEqualTo(dispatch.traceId());
        assertThat(find.parentId()).isEqualTo(dispatch.id());
        assertThat(find.tags()).containsEntry("driver.id", "driver-9").containsEntry("otel.status_code", "OK");
        assertThat(find.durationAsLong()).isEqualTo(300_000L);

        zipkin2.Span notify = byName.get("notify driver");
        assertThat(notify.kind()).isEqualTo(zipkin2.Span.Kind.CLIENT);
        assertThat(notify.traceId()).isEqualTo(dispatch.traceId());
        assertThat(notify.parentId()).isEqualTo(dispatch.id());
        assertThat(notify.tags()).containsEntry("otel.status_code", "ERROR").containsEntry("error", "");
        assertThat(notify.annotations()).extracting(Annotation::value).containsExactly("exception");

        assertThat(tracerProvider.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(exporterCount("seen", null)).isEqualTo(3L);
        assertThat(exporterCount("exported", true)).isEqualTo(3L);
    }

    @Test
    void simpleProcessorPostsEachSpanAsItEnds() throws Exception {
        ZipkinSpanExporter exporter = exporterBuilder().build();
        SdkTracerProvider tracerProvider = tracerProvider(SimpleSpanProcessor.create(exporter));
        DispatchService service = new DispatchService(tracerProvider.get(SCOPE_NAME, SCOPE_VERSION));

        service.dispatch("delivery-78", "driver-4", List.of("airport"));

        List<zipkin2.Span> spans = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            CollectorRequest request = collector.next();
            assertThat(request.path()).isEqualTo(SPANS_PATH);
            assertThat(request.contentType()).isEqualTo("application/json");
            List<zipkin2.Span> posted = SpanBytesDecoder.JSON_V2.decodeList(request.body());
            assertThat(posted).hasSize(1);
            spans.addAll(posted);
        }
        assertThat(spans).extracting(zipkin2.Span::name)
                .containsExactlyInAnyOrder("find driver", "notify driver", "dispatch");
        assertThat(spans).allSatisfy(span -> {
            assertThat(span.traceId()).isEqualTo(spans.get(0).traceId());
            assertThat(span.localServiceName()).isEqualTo("dispatch-service");
        });
        zipkin2.Span dispatch = spans.stream().filter(span -> span.name().equals("dispatch")).findFirst().get();
        assertThat(dispatch.tags()).containsEntry("delivery.zones", "airport");

        assertThat(tracerProvider.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(exporterCount("exported", true)).isEqualTo(3L);
    }

    @Test
    void unavailableCollectorFailsTheExport() throws Exception {
        InMemorySpanExporter recorder = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = tracerProvider(SimpleSpanProcessor.create(recorder));
        new DispatchService(tracerProvider.get(SCOPE_NAME, SCOPE_VERSION))
                .dispatch("delivery-79", "driver-2", List.of("old town"));
        List<SpanData> finished = recorder.getFinishedSpanItems();
        assertThat(finished).hasSize(3);

        collector.respondWith(503);
        SpanExporter exporter = exporterBuilder().build();
        CompletableResultCode result = exporter.export(finished).join(10, TimeUnit.SECONDS);

        assertThat(result.isDone()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        CollectorRequest request = collector.next();
        assertThat(request.contentType()).isEqualTo("application/json");
        assertThat(SpanBytesDecoder.JSON_V2.decodeList(request.body())).hasSize(3);
        assertThat(exporterCount("seen", null)).isEqualTo(3L);
        assertThat(exporterCount("exported", false)).isEqualTo(3L);
        assertThat(exporter.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(tracerProvider.shutdown().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    private ZipkinSpanExporterBuilder exporterBuilder() {
        return ZipkinSpanExporter.builder()
                .setEndpoint(collector.endpoint())
                .setReadTimeout(10, TimeUnit.SECONDS)
                .setLocalIpAddressSupplier(InetAddress::getLoopbackAddress)
                .setMeterProvider(meterProvider);
    }

    private static SdkTracerProvider tracerProvider(SpanProcessor processor) {
        return SdkTracerProvider.builder()
                .setResource(Resource.getDefault().merge(
                        Resource.create(Attributes.of(SERVICE_NAME, "dispatch-service"))))
                .addSpanProcessor(processor)
                .build();
    }

    /// Sums the exporter's own `seen` or `exported` counter, filtered by the `success` attribute.
    private long exporterCount(String counter, Boolean success) {
        return exporterMetrics.collectAllMetrics().stream()
                .filter(metric -> metric.getName().endsWith(".exporter." + counter))
                .map(MetricData::getLongSumData)
                .flatMap(sum -> sum.getPoints().stream())
                .filter(point -> success == null
                        || success.equals(point.getAttributes().get(AttributeKey.booleanKey("success"))))
                .mapToLong(LongPointData::getValue)
                .sum();
    }

    /** A span post the collector received. */
    record CollectorRequest(String method, String path, String contentType, String contentEncoding,
            byte[] body) {
    }

    /** Local Zipkin collector that accepts span posts, inflating gzip bodies, and queues them. */
    static final class ZipkinCollector {

        private final BlockingQueue<CollectorRequest> requests = new LinkedBlockingQueue<>();

        private volatile int status = 202;

        private HttpServer server;

        void start() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext(SPANS_PATH, this::handle);
            server.start();
        }

        void stop() {
            server.stop(0);
        }

        String endpoint() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + SPANS_PATH;
        }

        void respondWith(int newStatus) {
            status = newStatus;
        }

        List<CollectorRequest> received() {
            return List.copyOf(requests);
        }

        CollectorRequest next() throws InterruptedException {
            CollectorRequest request = requests.poll(10, TimeUnit.SECONDS);
            assertThat(request).as("span post received by the collector").isNotNull();
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
                    exchange.getRequestHeaders().getFirst("Content-Type"), encoding, body));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        }
    }

    /** Dispatch service instrumented with the OpenTelemetry tracing API, using fixed timestamps. */
    static final class DispatchService {

        private final Tracer tracer;

        DispatchService(Tracer tracer) {
            this.tracer = tracer;
        }

        String dispatch(String deliveryId, String driverId, List<String> zones) {
            Span span = tracer.spanBuilder("dispatch")
                    .setSpanKind(SpanKind.SERVER)
                    .setStartTimestamp(DISPATCH_START)
                    .setAttribute(DELIVERY_ID, deliveryId)
                    .setAttribute(PARCEL_COUNT, 3L)
                    .setAttribute(EXPRESS, true)
                    .setAttribute(ZONES, zones)
                    .startSpan();
            try (Scope ignored = span.makeCurrent()) {
                findDriver(driverId);
                span.addEvent("driver assigned", DISPATCH_START.plusMillis(400));
                try {
                    notifyDriver();
                } catch (IllegalStateException e) {
                    span.addEvent("notification failed", Attributes.of(DRIVER_ID, driverId),
                            DISPATCH_START.plusMillis(1200));
                    span.setStatus(StatusCode.ERROR, "driver unreachable");
                }
                return deliveryId + "->" + driverId;
            } finally {
                span.end(DISPATCH_START.plusMillis(1500));
            }
        }

        private void findDriver(String driverId) {
            Span span = tracer.spanBuilder("find driver")
                    .setStartTimestamp(DISPATCH_START.plusMillis(100))
                    .setAttribute(DRIVER_ID, driverId)
                    .startSpan();
            span.setStatus(StatusCode.OK);
            span.end(DISPATCH_START.plusMillis(400));
        }

        private void notifyDriver() {
            Span span = tracer.spanBuilder("notify driver")
                    .setSpanKind(SpanKind.CLIENT)
                    .setStartTimestamp(DISPATCH_START.plusMillis(500))
                    .startSpan();
            try {
                throw new IllegalStateException("push gateway refused the connection");
            } catch (IllegalStateException e) {
                span.recordException(e, Attributes.empty());
                span.setStatus(StatusCode.ERROR);
                throw e;
            } finally {
                span.end(DISPATCH_START.plusMillis(1100));
            }
        }
    }
}
