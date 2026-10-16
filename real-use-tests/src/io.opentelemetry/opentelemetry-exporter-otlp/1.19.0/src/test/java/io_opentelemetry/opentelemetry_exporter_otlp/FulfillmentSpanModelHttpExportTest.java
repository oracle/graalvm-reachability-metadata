/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_exporter_otlp;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Status;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.io.IOException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The OTLP span data model on the wire from `OtlpHttpSpanExporter` built in code over plain HTTP.
 *
 * A fulfillment service traces each order through two instrumented components: its HTTP API and
 * its picking-queue worker, each with its own tracer name, version, and schema URL. It tags spans
 * with typed and array attributes, records pick events, and runs under span limits that cap
 * attributes, attribute length, and events. The collector here is a MockWebServer that decodes
 * the posted `/v1/traces` protobuf, so the test asserts on how a real collector sees the resource
 * and scope grouping, span kinds and parentage, every attribute value type, status, the dropped
 * counts the limits leave behind, and on an exporter that was shut down before the last export.
 */
public class FulfillmentSpanModelHttpExportTest {

    private static final long WAIT_SECONDS = 20;

    private static final String RESOURCE_SCHEMA = "https://opentelemetry.io/schemas/1.12.0";

    private static final String API_SCHEMA = "https://opentelemetry.io/schemas/1.9.0";

    private static final AttributeKey<String> ORDER_ID = AttributeKey.stringKey("fulfillment.order.id");

    private static final AttributeKey<Long> LINE_COUNT = AttributeKey.longKey("fulfillment.order.lines");

    private static final AttributeKey<Double> WEIGHT_KG = AttributeKey.doubleKey("fulfillment.order.weight_kg");

    private static final AttributeKey<Boolean> EXPRESS = AttributeKey.booleanKey("fulfillment.order.express");

    private static final AttributeKey<List<String>> SKUS = AttributeKey.stringArrayKey("fulfillment.order.skus");

    private static final AttributeKey<List<Long>> BINS = AttributeKey.longArrayKey("fulfillment.pick.bins");

    private static final AttributeKey<List<Double>> DIMENSIONS_CM =
            AttributeKey.doubleArrayKey("fulfillment.parcel.dimensions_cm");

    private static final AttributeKey<List<Boolean>> FRAGILE_LINES =
            AttributeKey.booleanArrayKey("fulfillment.order.fragile_lines");

    private static final AttributeKey<String> NOTE = AttributeKey.stringKey("fulfillment.order.note");

    private MockWebServer collector;

    @BeforeEach
    void startCollector() throws IOException {
        collector = new MockWebServer();
        collector.start();
    }

    @AfterEach
    void stopCollector() throws IOException {
        collector.shutdown();
    }

    @Test
    void groupsSpansByResourceAndInstrumentationScope() throws Exception {
        acceptExport();
        SdkTracerProvider tracerProvider = batchingTracerProvider(httpExporter(), SpanLimits.getDefault());
        FulfillmentService fulfillment = new FulfillmentService(tracerProvider);

        Map<String, String> ids = fulfillment.fulfill("ord-1001", List.of("sku-7", "sku-9"));
        assertThat(tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();

        RecordedRequest request = collector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/v1/traces");
        assertThat(request.getHeader("Content-Type")).startsWith("application/x-protobuf");
        assertThat(request.getHeader("Content-Encoding")).isNull();
        ExportTraceServiceRequest export = ExportTraceServiceRequest.parseFrom(request.getBody().readByteArray());

        assertThat(export.getResourceSpansList()).singleElement().satisfies(resourceSpans -> {
            assertThat(resourceSpans.getSchemaUrl()).isEqualTo(RESOURCE_SCHEMA);
            assertThat(stringAttributes(resourceSpans.getResource().getAttributesList()))
                    .containsEntry(ResourceAttributes.SERVICE_NAME.getKey(), "fulfillment")
                    .containsEntry(ResourceAttributes.DEPLOYMENT_ENVIRONMENT.getKey(), "eu-west");
        });
        Map<String, ScopeSpans> scopes = export.getResourceSpans(0).getScopeSpansList().stream()
                .collect(Collectors.toMap(scopeSpans -> scopeSpans.getScope().getName(), Function.identity()));
        assertThat(scopes).containsOnlyKeys("fulfillment-api", "fulfillment-picking");

        ScopeSpans api = scopes.get("fulfillment-api");
        assertThat(api.getScope().getVersion()).isEqualTo("2.4.0");
        assertThat(api.getSchemaUrl()).isEqualTo(API_SCHEMA);
        assertThat(api.getSpansList()).extracting(io.opentelemetry.proto.trace.v1.Span::getName)
                .containsExactlyInAnyOrder("POST /orders", "publish picking-jobs");
        ScopeSpans picking = scopes.get("fulfillment-picking");
        assertThat(picking.getScope().getVersion()).isEqualTo("1.1.0");
        assertThat(picking.getSchemaUrl()).isEmpty();
        assertThat(picking.getSpansList()).extracting(io.opentelemetry.proto.trace.v1.Span::getName)
                .containsExactlyInAnyOrder("process picking-jobs", "pick sku-7", "pick sku-9");

        Map<String, io.opentelemetry.proto.trace.v1.Span> spans = spansByName(export);
        io.opentelemetry.proto.trace.v1.Span server = spans.get("POST /orders");
        assertThat(server.getKind()).isEqualTo(io.opentelemetry.proto.trace.v1.Span.SpanKind.SPAN_KIND_SERVER);
        assertThat(hex(server.getTraceId().toByteArray())).isEqualTo(ids.get("trace"));
        assertThat(hex(server.getSpanId().toByteArray())).isEqualTo(ids.get("server"));
        assertThat(server.getParentSpanId().isEmpty()).isTrue();
        assertThat(server.getEndTimeUnixNano()).isGreaterThanOrEqualTo(server.getStartTimeUnixNano());

        io.opentelemetry.proto.trace.v1.Span producer = spans.get("publish picking-jobs");
        assertThat(producer.getKind()).isEqualTo(io.opentelemetry.proto.trace.v1.Span.SpanKind.SPAN_KIND_PRODUCER);
        assertThat(hex(producer.getParentSpanId().toByteArray())).isEqualTo(ids.get("server"));

        io.opentelemetry.proto.trace.v1.Span consumer = spans.get("process picking-jobs");
        assertThat(consumer.getKind()).isEqualTo(io.opentelemetry.proto.trace.v1.Span.SpanKind.SPAN_KIND_CONSUMER);
        assertThat(hex(consumer.getParentSpanId().toByteArray())).isEqualTo(hex(producer.getSpanId().toByteArray()));
        assertThat(spans.get("pick sku-7").getKind())
                .isEqualTo(io.opentelemetry.proto.trace.v1.Span.SpanKind.SPAN_KIND_INTERNAL);
        assertThat(hex(spans.get("pick sku-9").getParentSpanId().toByteArray()))
                .isEqualTo(hex(consumer.getSpanId().toByteArray()));
        assertThat(spans.values()).allSatisfy(span ->
                assertThat(hex(span.getTraceId().toByteArray())).isEqualTo(ids.get("trace")));

        assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void encodesEveryAttributeValueTypeEventsAndStatus() throws Exception {
        acceptExport();
        SdkTracerProvider tracerProvider = batchingTracerProvider(httpExporter(), SpanLimits.getDefault());
        FulfillmentService fulfillment = new FulfillmentService(tracerProvider);

        fulfillment.fulfill("ord-1002", List.of("sku-3"));
        fulfillment.shipParcel("ord-1002", false);
        assertThat(tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();

        RecordedRequest request = collector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        Map<String, io.opentelemetry.proto.trace.v1.Span> spans =
                spansByName(ExportTraceServiceRequest.parseFrom(request.getBody().readByteArray()));

        io.opentelemetry.proto.trace.v1.Span server = spans.get("POST /orders");
        Map<String, AnyValue> attributes = values(server.getAttributesList());
        assertThat(attributes.get(ORDER_ID.getKey()).getStringValue()).isEqualTo("ord-1002");
        assertThat(attributes.get(LINE_COUNT.getKey()).getIntValue()).isEqualTo(1L);
        assertThat(attributes.get(WEIGHT_KG.getKey()).getDoubleValue()).isEqualTo(2.75);
        assertThat(attributes.get(EXPRESS.getKey()).getBoolValue()).isTrue();
        assertThat(attributes.get(SKUS.getKey()).getArrayValue().getValuesList())
                .extracting(AnyValue::getStringValue).containsExactly("sku-3");
        assertThat(attributes.get(FRAGILE_LINES.getKey()).getArrayValue().getValuesList())
                .extracting(AnyValue::getBoolValue).containsExactly(false);
        assertThat(server.getStatus().getCode()).isEqualTo(Status.StatusCode.STATUS_CODE_OK);

        io.opentelemetry.proto.trace.v1.Span pick = spans.get("pick sku-3");
        assertThat(values(pick.getAttributesList()).get(BINS.getKey()).getArrayValue().getValuesList())
                .extracting(AnyValue::getIntValue).containsExactly(12L, 40L);
        assertThat(pick.getEventsList()).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("item scanned");
            assertThat(event.getTimeUnixNano())
                    .isBetween(pick.getStartTimeUnixNano(), pick.getEndTimeUnixNano());
            assertThat(values(event.getAttributesList()).get("fulfillment.scan.station").getStringValue())
                    .isEqualTo("station-4");
        });
        assertThat(pick.getStatus().getCode()).isEqualTo(Status.StatusCode.STATUS_CODE_UNSET);

        io.opentelemetry.proto.trace.v1.Span parcel = spans.get("ship parcel");
        assertThat(parcel.getKind()).isEqualTo(io.opentelemetry.proto.trace.v1.Span.SpanKind.SPAN_KIND_CLIENT);
        assertThat(values(parcel.getAttributesList()).get(DIMENSIONS_CM.getKey()).getArrayValue().getValuesList())
                .extracting(AnyValue::getDoubleValue).containsExactly(30.0, 20.5, 12.0);
        assertThat(parcel.getStatus().getCode()).isEqualTo(Status.StatusCode.STATUS_CODE_ERROR);
        assertThat(parcel.getStatus().getMessage()).isEqualTo("carrier rejected the parcel");
        assertThat(parcel.getEventsList()).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("exception");
            Map<String, AnyValue> exception = values(event.getAttributesList());
            assertThat(exception.get("exception.type").getStringValue())
                    .isEqualTo(IllegalStateException.class.getName());
            assertThat(exception.get("exception.message").getStringValue()).isEqualTo("label printer offline");
            assertThat(exception.get("exception.stacktrace").getStringValue())
                    .contains("FulfillmentService.shipParcel");
        });

        assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void reportsWhatTheSpanLimitsDropped() throws Exception {
        acceptExport();
        SpanLimits limits = SpanLimits.builder()
                .setMaxNumberOfAttributes(3)
                .setMaxAttributeValueLength(10)
                .setMaxNumberOfEvents(2)
                .setMaxNumberOfAttributesPerEvent(1)
                .build();
        SdkTracerProvider tracerProvider = batchingTracerProvider(httpExporter(), limits);
        Tracer tracer = tracerProvider.get("fulfillment-api", "2.4.0");

        Span order = tracer.spanBuilder("POST /orders").setSpanKind(SpanKind.SERVER).startSpan();
        order.setAttribute(ORDER_ID, "ord-1003");
        order.setAttribute(NOTE, "leave the parcel with the neighbour at number 12");
        order.setAttribute(SKUS, List.of("sku-1", "sku-2-oversized-pallet"));
        order.setAttribute(LINE_COUNT, 2L);
        order.setAttribute(EXPRESS, true);
        for (int attempt = 1; attempt <= 4; attempt++) {
            order.addEvent("stock checked", Attributes.of(
                    AttributeKey.longKey("fulfillment.stock.attempt"), (long) attempt,
                    AttributeKey.stringKey("fulfillment.stock.warehouse"), "berlin"));
        }
        order.end();
        assertThat(tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();

        RecordedRequest request = collector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        io.opentelemetry.proto.trace.v1.Span span = spansByName(
                ExportTraceServiceRequest.parseFrom(request.getBody().readByteArray())).get("POST /orders");

        Map<String, AnyValue> attributes = values(span.getAttributesList());
        assertThat(attributes).containsOnlyKeys(ORDER_ID.getKey(), NOTE.getKey(), SKUS.getKey());
        assertThat(span.getDroppedAttributesCount()).isEqualTo(2);
        assertThat(attributes.get(ORDER_ID.getKey()).getStringValue()).isEqualTo("ord-1003");
        assertThat(attributes.get(NOTE.getKey()).getStringValue()).isEqualTo("leave the ");
        assertThat(attributes.get(SKUS.getKey()).getArrayValue().getValuesList())
                .extracting(AnyValue::getStringValue).containsExactly("sku-1", "sku-2-over");

        assertThat(span.getEventsList()).hasSize(2).allSatisfy(event -> {
            assertThat(event.getName()).isEqualTo("stock checked");
            assertThat(event.getAttributesList()).hasSize(1);
            assertThat(event.getDroppedAttributesCount()).isEqualTo(1);
        });
        assertThat(span.getDroppedEventsCount()).isEqualTo(2);

        assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void refusesToExportOnceTheExporterIsShutDown() throws Exception {
        acceptExport();
        ExportOutcomes outcomes = new ExportOutcomes(httpExporter());
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(fulfillmentResource())
                .addSpanProcessor(SimpleSpanProcessor.create(outcomes))
                .build();
        FulfillmentService fulfillment = new FulfillmentService(tracerProvider);

        fulfillment.shipParcel("ord-1004", true);
        assertThat(tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
        RecordedRequest delivered = collector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(delivered).isNotNull();
        assertThat(spansByName(ExportTraceServiceRequest.parseFrom(delivered.getBody().readByteArray())))
                .containsOnlyKeys("ship parcel");

        assertThat(outcomes.delegate.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
        fulfillment.shipParcel("ord-1005", true);
        assertThat(tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();

        assertThat(outcomes.results).containsExactly(true, false);
        assertThat(collector.getRequestCount()).isEqualTo(1);
        tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private void acceptExport() {
        collector.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/x-protobuf")
                .setBody(new Buffer().write(ExportTraceServiceResponse.getDefaultInstance().toByteArray())));
    }

    private OtlpHttpSpanExporter httpExporter() {
        return OtlpHttpSpanExporter.builder()
                .setEndpoint(collector.url("/v1/traces").toString())
                .setTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    private static Resource fulfillmentResource() {
        return Resource.getDefault().merge(Resource.create(Attributes.of(
                ResourceAttributes.SERVICE_NAME, "fulfillment",
                ResourceAttributes.DEPLOYMENT_ENVIRONMENT, "eu-west"), RESOURCE_SCHEMA));
    }

    private static SdkTracerProvider batchingTracerProvider(SpanExporter exporter, SpanLimits limits) {
        return SdkTracerProvider.builder()
                .setResource(fulfillmentResource())
                .setSpanLimits(limits)
                .addSpanProcessor(BatchSpanProcessor.builder(exporter)
                        .setScheduleDelay(10, TimeUnit.MINUTES)
                        .build())
                .build();
    }

    private static Map<String, io.opentelemetry.proto.trace.v1.Span> spansByName(ExportTraceServiceRequest export) {
        return export.getResourceSpansList().stream()
                .map(ResourceSpans::getScopeSpansList)
                .flatMap(List::stream)
                .flatMap(scopeSpans -> scopeSpans.getSpansList().stream())
                .collect(Collectors.toMap(io.opentelemetry.proto.trace.v1.Span::getName, Function.identity()));
    }

    private static Map<String, AnyValue> values(List<KeyValue> keyValues) {
        return keyValues.stream().collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue));
    }

    private static Map<String, String> stringAttributes(List<KeyValue> keyValues) {
        return keyValues.stream()
                .collect(Collectors.toMap(KeyValue::getKey, keyValue -> keyValue.getValue().getStringValue()));
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    /// The application code under measurement: an order API that hands picking jobs to a worker.
    private static final class FulfillmentService {

        private final Tracer api;

        private final Tracer picking;

        FulfillmentService(SdkTracerProvider tracerProvider) {
            api = tracerProvider.tracerBuilder("fulfillment-api")
                    .setInstrumentationVersion("2.4.0")
                    .setSchemaUrl(API_SCHEMA)
                    .build();
            picking = tracerProvider.get("fulfillment-picking", "1.1.0");
        }

        /// Accepts an order, publishes its picking job, and lets the worker pick every SKU.
        /// Returns the hex trace id and server span id the collector should see.
        Map<String, String> fulfill(String orderId, List<String> skus) {
            Span order = api.spanBuilder("POST /orders")
                    .setSpanKind(SpanKind.SERVER)
                    .setAttribute(ORDER_ID, orderId)
                    .setAttribute(LINE_COUNT, (long) skus.size())
                    .setAttribute(WEIGHT_KG, 2.75)
                    .setAttribute(EXPRESS, true)
                    .setAttribute(SKUS, skus)
                    .setAttribute(FRAGILE_LINES, skus.stream().map(sku -> false).collect(Collectors.toList()))
                    .startSpan();
            try (Scope ignored = order.makeCurrent()) {
                Span publish = api.spanBuilder("publish picking-jobs").setSpanKind(SpanKind.PRODUCER).startSpan();
                Context job = Context.current().with(publish);
                publish.end();
                pickAll(job, skus);
                order.setStatus(StatusCode.OK);
            } finally {
                order.end();
            }
            return Map.of(
                    "trace", order.getSpanContext().getTraceId(),
                    "server", order.getSpanContext().getSpanId());
        }

        private void pickAll(Context job, List<String> skus) {
            Span process = picking.spanBuilder("process picking-jobs")
                    .setParent(job)
                    .setSpanKind(SpanKind.CONSUMER)
                    .startSpan();
            try (Scope ignored = process.makeCurrent()) {
                for (String sku : skus) {
                    Span pick = picking.spanBuilder("pick " + sku)
                            .setAttribute(BINS, List.of(12L, 40L))
                            .startSpan();
                    pick.addEvent("item scanned", Attributes.of(
                            AttributeKey.stringKey("fulfillment.scan.station"), "station-4"));
                    pick.end();
                }
            } finally {
                process.end();
            }
        }

        void shipParcel(String orderId, boolean printerOnline) {
            Span parcel = api.spanBuilder("ship parcel")
                    .setSpanKind(SpanKind.CLIENT)
                    .setAttribute(ORDER_ID, orderId)
                    .setAttribute(DIMENSIONS_CM, List.of(30.0, 20.5, 12.0))
                    .startSpan();
            try {
                if (!printerOnline) {
                    throw new IllegalStateException("label printer offline");
                }
            } catch (IllegalStateException e) {
                parcel.recordException(e);
                parcel.setStatus(StatusCode.ERROR, "carrier rejected the parcel");
            } finally {
                parcel.end();
            }
        }
    }

    /// A span exporter decorator that records each export outcome for the service's health page.
    private static final class ExportOutcomes implements SpanExporter {

        private final SpanExporter delegate;

        private final List<Boolean> results = new CopyOnWriteArrayList<>();

        ExportOutcomes(SpanExporter delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            CompletableResultCode result = delegate.export(spans);
            result.whenComplete(() -> results.add(result.isSuccess()));
            return result;
        }

        @Override
        public CompletableResultCode flush() {
            return delegate.flush();
        }

        @Override
        public CompletableResultCode shutdown() {
            return delegate.shutdown();
        }
    }
}
