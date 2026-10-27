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
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.AggregationTemporality;
import io.opentelemetry.proto.metrics.v1.HistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Metric export over OTLP/HTTP with `OtlpHttpMetricExporter` and a periodic metric reader.
 *
 * An inventory service counts reservations, records how long each lookup takes, tracks stock on
 * hand with an up-down counter, and reports the warehouse queue depth from an asynchronous gauge.
 * A periodic reader collects those instruments and the exporter posts them as protobuf to the
 * collector's `/v1/metrics` endpoint. The collector here is a MockWebServer that decodes the OTLP
 * request, so the test asserts on the metric points a real collector would store, on delta versus
 * cumulative temporality, on gzip compression, and on how the service sees a collector outage.
 */
public class InventoryMetricsHttpExportTest {

    private static final long WAIT_SECONDS = 20;

    private static final AttributeKey<String> WAREHOUSE = AttributeKey.stringKey("inventory.warehouse");

    private static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("inventory.outcome");

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
    void postsCumulativeInventoryMetricsAsProtobuf() throws Exception {
        acceptExport();
        OtlpHttpMetricExporter exporter = OtlpHttpMetricExporter.builder()
                .setEndpoint(metricsEndpoint())
                .setTimeout(Duration.ofSeconds(10))
                .addHeader("x-tenant", "acme-retail")
                .build();
        SdkMeterProvider meterProvider = meterProvider(exporter);
        AtomicLong queueDepth = new AtomicLong(7);
        InventoryService inventory = new InventoryService(meterProvider.get("inventory-service"), queueDepth);

        inventory.reserve("berlin", 3, true, 12.5);
        inventory.reserve("berlin", 2, true, 30.0);
        inventory.reserve("lisbon", 4, false, 180.0);
        inventory.restock("lisbon", 10);
        CompletableResultCode flushed = meterProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(flushed.isSuccess()).isTrue();
        RecordedRequest request = collector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/v1/metrics");
        assertThat(request.getHeader("Content-Type")).startsWith("application/x-protobuf");
        assertThat(request.getHeader("x-tenant")).isEqualTo("acme-retail");

        ExportMetricsServiceRequest export = ExportMetricsServiceRequest.parseFrom(request.getBody().readByteArray());
        assertThat(attributes(export.getResourceMetrics(0).getResource().getAttributesList()))
                .containsEntry(ResourceAttributes.SERVICE_NAME.getKey(), "inventory");
        assertThat(export.getResourceMetrics(0).getScopeMetrics(0).getScope().getName())
                .isEqualTo("inventory-service");
        Map<String, Metric> metrics = metricsByName(export);
        assertThat(metrics).containsOnlyKeys(
                "inventory.reservations", "inventory.lookup.duration", "inventory.stock", "inventory.queue.depth");

        Metric reservations = metrics.get("inventory.reservations");
        assertThat(reservations.getUnit()).isEqualTo("{reservation}");
        assertThat(reservations.getSum().getIsMonotonic()).isTrue();
        assertThat(reservations.getSum().getAggregationTemporality())
                .isEqualTo(AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE);
        assertThat(pointsByOutcome(reservations.getSum().getDataPointsList()))
                .containsEntry("berlin/reserved", 5L)
                .containsEntry("lisbon/out_of_stock", 4L)
                .hasSize(2);

        Metric lookups = metrics.get("inventory.lookup.duration");
        assertThat(lookups.getUnit()).isEqualTo("ms");
        Map<String, HistogramDataPoint> lookupsByWarehouse = lookups.getHistogram().getDataPointsList().stream()
                .collect(Collectors.toMap(
                        point -> attributes(point.getAttributesList()).get(WAREHOUSE.getKey()), Function.identity()));
        assertThat(lookupsByWarehouse.get("berlin").getCount()).isEqualTo(2);
        assertThat(lookupsByWarehouse.get("berlin").getSum()).isEqualTo(42.5);
        assertThat(lookupsByWarehouse.get("berlin").getMax()).isEqualTo(30.0);
        assertThat(lookupsByWarehouse.get("lisbon").getCount()).isEqualTo(1);
        assertThat(lookupsByWarehouse.get("lisbon").getExplicitBoundsList()).isNotEmpty();
        assertThat(lookupsByWarehouse.get("lisbon").getBucketCountsList().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(1);

        Metric stock = metrics.get("inventory.stock");
        assertThat(stock.getSum().getIsMonotonic()).isFalse();
        assertThat(pointsByWarehouse(stock.getSum().getDataPointsList()))
                .containsEntry("berlin", -5L)
                .containsEntry("lisbon", 10L);

        Metric queue = metrics.get("inventory.queue.depth");
        assertThat(queue.getGauge().getDataPointsList()).singleElement()
                .satisfies(point -> assertThat(point.getAsInt()).isEqualTo(7));

        assertThat(meterProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void exportsDeltasPerCollectionWhenDeltaIsPreferred() throws Exception {
        acceptExport();
        acceptExport();
        OtlpHttpMetricExporter exporter = OtlpHttpMetricExporter.builder()
                .setEndpoint(metricsEndpoint())
                .setAggregationTemporalitySelector(AggregationTemporalitySelector.deltaPreferred())
                .build();
        SdkMeterProvider meterProvider = meterProvider(exporter);
        InventoryService inventory =
                new InventoryService(meterProvider.get("inventory-service"), new AtomicLong(0));

        inventory.reserve("berlin", 3, true, 10.0);
        assertThat(meterProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
        inventory.reserve("berlin", 4, true, 20.0);
        assertThat(meterProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();

        List<Long> berlinReservations = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            RecordedRequest request = collector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            Metric reservations = metricsByName(ExportMetricsServiceRequest.parseFrom(
                    request.getBody().readByteArray())).get("inventory.reservations");
            assertThat(reservations.getSum().getAggregationTemporality())
                    .isEqualTo(AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA);
            berlinReservations.add(pointsByOutcome(reservations.getSum().getDataPointsList()).get("berlin/reserved"));
        }
        assertThat(berlinReservations).containsExactly(3L, 4L);
        assertThat(meterProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void gzipsTheRequestBodyWhenConfigured() throws Exception {
        acceptExport();
        OtlpHttpMetricExporter exporter = OtlpHttpMetricExporter.builder()
                .setEndpoint(metricsEndpoint())
                .setCompression("gzip")
                .build();
        SdkMeterProvider meterProvider = meterProvider(exporter);
        InventoryService inventory =
                new InventoryService(meterProvider.get("inventory-service"), new AtomicLong(2));

        inventory.reserve("oslo", 1, true, 5.0);
        assertThat(meterProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();

        RecordedRequest request = collector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getHeader("Content-Encoding")).isEqualTo("gzip");
        ExportMetricsServiceRequest export = ExportMetricsServiceRequest.parseFrom(gunzip(request.getBody()));
        assertThat(pointsByOutcome(metricsByName(export).get("inventory.reservations").getSum().getDataPointsList()))
                .containsExactly(Map.entry("oslo/reserved", 1L));
        assertThat(meterProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void reportsACollectorOutageAndDeliversTheNextCollection() throws Exception {
        collector.enqueue(new MockResponse().setResponseCode(503).setBody("collector overloaded"));
        acceptExport();
        ExportOutcomes outcomes = new ExportOutcomes(OtlpHttpMetricExporter.builder()
                .setEndpoint(metricsEndpoint())
                .build());
        SdkMeterProvider meterProvider = meterProvider(outcomes);
        InventoryService inventory =
                new InventoryService(meterProvider.get("inventory-service"), new AtomicLong(1));

        inventory.reserve("berlin", 2, true, 8.0);
        meterProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);
        inventory.reserve("berlin", 1, true, 9.0);
        meterProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(outcomes.results).containsExactly(false, true);
        assertThat(collector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS)).isNotNull();
        RecordedRequest delivered = collector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(delivered).isNotNull();
        Metric reservations = metricsByName(ExportMetricsServiceRequest.parseFrom(
                delivered.getBody().readByteArray())).get("inventory.reservations");
        assertThat(pointsByOutcome(reservations.getSum().getDataPointsList())).containsEntry("berlin/reserved", 3L);
        assertThat(meterProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    private void acceptExport() {
        collector.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/x-protobuf")
                .setBody(new Buffer().write(ExportMetricsServiceResponse.getDefaultInstance().toByteArray())));
    }

    private String metricsEndpoint() {
        return collector.url("/v1/metrics").toString();
    }

    private static SdkMeterProvider meterProvider(MetricExporter exporter) {
        return SdkMeterProvider.builder()
                .setResource(Resource.getDefault().merge(Resource.create(Attributes.of(
                        ResourceAttributes.SERVICE_NAME, "inventory"))))
                .registerMetricReader(PeriodicMetricReader.builder(exporter)
                        .setInterval(Duration.ofMinutes(10))
                        .build())
                .build();
    }

    private static byte[] gunzip(Buffer body) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(body.readByteArray()))) {
            return in.readAllBytes();
        }
    }

    private static Map<String, Metric> metricsByName(ExportMetricsServiceRequest export) {
        return export.getResourceMetricsList().stream()
                .flatMap(resourceMetrics -> resourceMetrics.getScopeMetricsList().stream())
                .flatMap(scopeMetrics -> scopeMetrics.getMetricsList().stream())
                .collect(Collectors.toMap(Metric::getName, Function.identity()));
    }

    private static Map<String, Long> pointsByOutcome(List<NumberDataPoint> points) {
        return points.stream().collect(Collectors.toMap(point -> {
            Map<String, String> attributes = attributes(point.getAttributesList());
            return attributes.get(WAREHOUSE.getKey()) + "/" + attributes.get(OUTCOME.getKey());
        }, NumberDataPoint::getAsInt));
    }

    private static Map<String, Long> pointsByWarehouse(List<NumberDataPoint> points) {
        return points.stream().collect(Collectors.toMap(
                point -> attributes(point.getAttributesList()).get(WAREHOUSE.getKey()), NumberDataPoint::getAsInt));
    }

    private static Map<String, String> attributes(List<KeyValue> keyValues) {
        return keyValues.stream()
                .collect(Collectors.toMap(KeyValue::getKey, keyValue -> keyValue.getValue().getStringValue()));
    }

    /// The application code under measurement: reservations, lookups, stock levels, and queue depth.
    private static final class InventoryService {

        private final LongCounter reservations;

        private final DoubleHistogram lookupDuration;

        private final LongUpDownCounter stock;

        InventoryService(Meter meter, AtomicLong queueDepth) {
            reservations = meter.counterBuilder("inventory.reservations")
                    .setDescription("Units requested by reservations")
                    .setUnit("{reservation}")
                    .build();
            lookupDuration = meter.histogramBuilder("inventory.lookup.duration")
                    .setDescription("Time spent looking up stock")
                    .setUnit("ms")
                    .build();
            stock = meter.upDownCounterBuilder("inventory.stock")
                    .setDescription("Units on hand")
                    .build();
            meter.gaugeBuilder("inventory.queue.depth")
                    .ofLongs()
                    .setDescription("Picking jobs waiting in the warehouse queue")
                    .buildWithCallback(measurement -> measurement.record(queueDepth.get()));
        }

        void reserve(String warehouse, long units, boolean inStock, double lookupMillis) {
            Attributes where = Attributes.of(WAREHOUSE, warehouse);
            lookupDuration.record(lookupMillis, where);
            reservations.add(units, Attributes.of(WAREHOUSE, warehouse, OUTCOME, inStock ? "reserved" : "out_of_stock"));
            if (inStock) {
                stock.add(-units, where);
            }
        }

        void restock(String warehouse, long units) {
            stock.add(units, Attributes.of(WAREHOUSE, warehouse));
        }
    }

    /// A metric exporter decorator that records each export outcome for the service's health page.
    private static final class ExportOutcomes implements MetricExporter {

        private final MetricExporter delegate;

        private final List<Boolean> results = new CopyOnWriteArrayList<>();

        ExportOutcomes(MetricExporter delegate) {
            this.delegate = delegate;
        }

        @Override
        public io.opentelemetry.sdk.metrics.data.AggregationTemporality getAggregationTemporality(
                InstrumentType instrumentType) {
            return delegate.getAggregationTemporality(instrumentType);
        }

        @Override
        public Aggregation getDefaultAggregation(InstrumentType instrumentType) {
            return delegate.getDefaultAggregation(instrumentType);
        }

        @Override
        public CompletableResultCode export(Collection<MetricData> metrics) {
            CompletableResultCode result = delegate.export(metrics);
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
