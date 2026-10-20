/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_exporter_otlp;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
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
import io.opentelemetry.sdk.metrics.export.DefaultAggregationSelector;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Metric export over OTLP/gRPC with `OtlpGrpcMetricExporter` on its default transport, with the
 * exporter choosing aggregations (`setDefaultAggregationSelector`) and temporality
 * (`setAggregationTemporalitySelector`).
 *
 * A ride-hailing dispatcher counts booked rides, records how long each fare quote takes, tracks
 * the rides in progress with an up-down counter, and reports the vehicles online from an
 * asynchronous gauge. A periodic reader hands those instruments to the gRPC metric exporter,
 * which ships them to an in-process collector implementing the OTLP metrics service. The test
 * asserts on the points a real collector would store across successive collection intervals, on
 * the SLO histogram buckets the exporter selects, and on how a rejected export is observed.
 */
public class RideLatencyGrpcMetricsTest {

    private static final long WAIT_SECONDS = 20;

    private static final Metadata.Key<String> API_KEY =
            Metadata.Key.of("x-metrics-api-key", Metadata.ASCII_STRING_MARSHALLER);

    private static final AttributeKey<String> CITY = AttributeKey.stringKey("ride.city");

    private static final List<Double> SLO_BOUNDARIES = Arrays.asList(100.0, 300.0, 1000.0);

    private Collector collector;

    private Server server;

    @BeforeEach
    void startCollector() throws IOException {
        collector = new Collector();
        server = NettyServerBuilder.forPort(0)
                .addService(ServerInterceptors.intercept(collector, collector.headerRecorder()))
                .build()
                .start();
    }

    @AfterEach
    void stopCollector() throws InterruptedException {
        server.shutdownNow();
        server.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void shipsCumulativeRideMetricsOnEveryInterval() {
        OtlpGrpcMetricExporter exporter = OtlpGrpcMetricExporter.builder()
                .setEndpoint(collectorEndpoint())
                .setTimeout(Duration.ofSeconds(10))
                .addHeader(API_KEY.name(), "dispatch-secret")
                .build();
        assertThat(exporter.getAggregationTemporality(InstrumentType.COUNTER))
                .isEqualTo(io.opentelemetry.sdk.metrics.data.AggregationTemporality.CUMULATIVE);
        SdkMeterProvider meterProvider = meterProvider(exporter);
        AtomicLong vehiclesOnline = new AtomicLong(42);
        Dispatcher dispatcher = new Dispatcher(meterProvider.get("ride-dispatcher"), vehiclesOnline);

        dispatcher.bookRide("lagos", 80.0);
        dispatcher.bookRide("lagos", 240.0);
        dispatcher.bookRide("nairobi", 1200.0);
        assertThat(meterProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
        dispatcher.finishRide("lagos");
        dispatcher.bookRide("lagos", 30.0);
        vehiclesOnline.set(39);
        assertThat(meterProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();

        assertThat(collector.requests).hasSize(2);
        assertThat(collector.headers).hasSize(2).allSatisfy(
                headers -> assertThat(headers.get(API_KEY)).isEqualTo("dispatch-secret"));
        ExportMetricsServiceRequest latest = collector.requests.get(1);
        assertThat(attributes(latest.getResourceMetrics(0).getResource().getAttributesList()))
                .containsEntry(ResourceAttributes.SERVICE_NAME.getKey(), "ride-dispatch");
        assertThat(latest.getResourceMetrics(0).getScopeMetrics(0).getScope().getName())
                .isEqualTo("ride-dispatcher");

        Map<String, Metric> metrics = metricsByName(latest);
        assertThat(metrics).containsOnlyKeys(
                "rides.booked", "rides.quote_duration", "rides.in_progress", "rides.vehicles_online");

        Metric booked = metrics.get("rides.booked");
        assertThat(booked.getUnit()).isEqualTo("{rides}");
        assertThat(booked.getSum().getIsMonotonic()).isTrue();
        assertThat(booked.getSum().getAggregationTemporality())
                .isEqualTo(AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE);
        assertThat(pointsByCity(booked.getSum().getDataPointsList()))
                .containsEntry("lagos", 3L)
                .containsEntry("nairobi", 1L);

        Metric inProgress = metrics.get("rides.in_progress");
        assertThat(inProgress.getSum().getIsMonotonic()).isFalse();
        assertThat(pointsByCity(inProgress.getSum().getDataPointsList()))
                .containsEntry("lagos", 2L)
                .containsEntry("nairobi", 1L);

        assertThat(metrics.get("rides.vehicles_online").getGauge().getDataPointsList())
                .singleElement()
                .satisfies(point -> assertThat(point.getAsInt()).isEqualTo(39L));

        Metric quotes = metrics.get("rides.quote_duration");
        assertThat(quotes.getUnit()).isEqualTo("ms");
        Map<String, HistogramDataPoint> quotesByCity = histogramByCity(quotes);
        HistogramDataPoint lagosQuotes = quotesByCity.get("lagos");
        assertThat(lagosQuotes.getCount()).isEqualTo(3L);
        assertThat(lagosQuotes.getSum()).isEqualTo(350.0);
        assertThat(lagosQuotes.getMin()).isEqualTo(30.0);
        assertThat(lagosQuotes.getMax()).isEqualTo(240.0);
        assertThat(lagosQuotes.getExplicitBoundsList()).containsExactly(
                5.0, 10.0, 25.0, 50.0, 75.0, 100.0, 250.0, 500.0, 750.0, 1000.0, 2500.0, 5000.0, 7500.0,
                10000.0);
        assertThat(quotesByCity.get("nairobi").getCount()).isEqualTo(1L);

        NumberDataPoint firstLagos = collector.requests.get(0).getResourceMetrics(0).getScopeMetrics(0)
                .getMetricsList().stream()
                .filter(metric -> metric.getName().equals("rides.booked"))
                .flatMap(metric -> metric.getSum().getDataPointsList().stream())
                .filter(point -> city(point.getAttributesList()).equals("lagos"))
                .findFirst()
                .orElseThrow(IllegalStateException::new);
        NumberDataPoint latestLagos = booked.getSum().getDataPointsList().stream()
                .filter(point -> city(point.getAttributesList()).equals("lagos"))
                .findFirst()
                .orElseThrow(IllegalStateException::new);
        assertThat(firstLagos.getAsInt()).isEqualTo(2L);
        assertThat(latestLagos.getStartTimeUnixNano()).isEqualTo(firstLagos.getStartTimeUnixNano());
        assertThat(latestLagos.getTimeUnixNano()).isGreaterThan(firstLagos.getTimeUnixNano());

        assertThat(meterProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void bucketsQuoteLatencyByTheSloBoundariesTheExporterSelects() {
        OtlpGrpcMetricExporter exporter = OtlpGrpcMetricExporter.builder()
                .setEndpoint(collectorEndpoint())
                .setTimeout(Duration.ofSeconds(10))
                .setDefaultAggregationSelector(DefaultAggregationSelector.getDefault()
                        .with(InstrumentType.HISTOGRAM, Aggregation.explicitBucketHistogram(SLO_BOUNDARIES)))
                .build();
        SdkMeterProvider meterProvider = meterProvider(exporter);
        Dispatcher dispatcher = new Dispatcher(meterProvider.get("ride-dispatcher"), new AtomicLong(10));

        for (double quoteMillis : new double[] {50.0, 120.0, 250.0, 900.0, 1500.0}) {
            dispatcher.bookRide("accra", quoteMillis);
        }
        assertThat(meterProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();

        assertThat(collector.requests).hasSize(1);
        Map<String, Metric> metrics = metricsByName(collector.requests.get(0));
        HistogramDataPoint accraQuotes = histogramByCity(metrics.get("rides.quote_duration")).get("accra");
        assertThat(accraQuotes.getExplicitBoundsList()).containsExactlyElementsOf(SLO_BOUNDARIES);
        assertThat(accraQuotes.getBucketCountsList()).containsExactly(1L, 2L, 1L, 1L);
        assertThat(accraQuotes.getCount()).isEqualTo(5L);
        assertThat(accraQuotes.getSum()).isEqualTo(2820.0);
        assertThat(pointsByCity(metrics.get("rides.booked").getSum().getDataPointsList()))
                .containsEntry("accra", 5L);

        assertThat(meterProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void reportsPerIntervalDeltasWhenDeltaIsPreferred() {
        OtlpGrpcMetricExporter exporter = OtlpGrpcMetricExporter.builder()
                .setEndpoint(collectorEndpoint())
                .setTimeout(Duration.ofSeconds(10))
                .setAggregationTemporalitySelector(AggregationTemporalitySelector.deltaPreferred())
                .build();
        assertThat(exporter.getAggregationTemporality(InstrumentType.COUNTER))
                .isEqualTo(io.opentelemetry.sdk.metrics.data.AggregationTemporality.DELTA);
        assertThat(exporter.getAggregationTemporality(InstrumentType.UP_DOWN_COUNTER))
                .isEqualTo(io.opentelemetry.sdk.metrics.data.AggregationTemporality.CUMULATIVE);
        SdkMeterProvider meterProvider = meterProvider(exporter);
        Dispatcher dispatcher = new Dispatcher(meterProvider.get("ride-dispatcher"), new AtomicLong(5));

        dispatcher.bookRide("cairo", 90.0);
        dispatcher.bookRide("cairo", 110.0);
        dispatcher.bookRide("cairo", 70.0);
        assertThat(meterProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
        dispatcher.bookRide("cairo", 400.0);
        dispatcher.bookRide("cairo", 60.0);
        assertThat(meterProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();

        assertThat(collector.requests).hasSize(2);
        Metric firstBooked = metricsByName(collector.requests.get(0)).get("rides.booked");
        Map<String, Metric> second = metricsByName(collector.requests.get(1));
        Metric secondBooked = second.get("rides.booked");
        assertThat(secondBooked.getSum().getAggregationTemporality())
                .isEqualTo(AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA);
        assertThat(pointsByCity(firstBooked.getSum().getDataPointsList())).containsEntry("cairo", 3L);
        assertThat(pointsByCity(secondBooked.getSum().getDataPointsList())).containsEntry("cairo", 2L);
        assertThat(secondBooked.getSum().getDataPoints(0).getStartTimeUnixNano())
                .isEqualTo(firstBooked.getSum().getDataPoints(0).getTimeUnixNano());

        Metric secondQuotes = second.get("rides.quote_duration");
        assertThat(secondQuotes.getHistogram().getAggregationTemporality())
                .isEqualTo(AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA);
        HistogramDataPoint cairoQuotes = histogramByCity(secondQuotes).get("cairo");
        assertThat(cairoQuotes.getCount()).isEqualTo(2L);
        assertThat(cairoQuotes.getSum()).isEqualTo(460.0);

        Metric inProgress = second.get("rides.in_progress");
        assertThat(inProgress.getSum().getAggregationTemporality())
                .isEqualTo(AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE);
        assertThat(pointsByCity(inProgress.getSum().getDataPointsList())).containsEntry("cairo", 5L);

        assertThat(meterProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void carriesCumulativeTotalsPastARejectedExport() {
        ExportOutcomes outcomes = new ExportOutcomes(OtlpGrpcMetricExporter.builder()
                .setEndpoint(collectorEndpoint())
                .setTimeout(Duration.ofSeconds(10))
                .build());
        SdkMeterProvider meterProvider = meterProvider(outcomes);
        Dispatcher dispatcher = new Dispatcher(meterProvider.get("ride-dispatcher"), new AtomicLong(8));

        collector.rejectNext(Status.UNAVAILABLE.withDescription("collector is draining"));
        dispatcher.bookRide("dakar", 150.0);
        dispatcher.bookRide("dakar", 210.0);
        meterProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);
        dispatcher.bookRide("dakar", 95.0);
        meterProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(outcomes.results).containsExactly(false, true);
        assertThat(collector.requests).hasSize(1);
        Map<String, Metric> metrics = metricsByName(collector.requests.get(0));
        assertThat(pointsByCity(metrics.get("rides.booked").getSum().getDataPointsList()))
                .containsEntry("dakar", 3L);
        assertThat(histogramByCity(metrics.get("rides.quote_duration")).get("dakar").getCount()).isEqualTo(3L);

        assertThat(meterProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    private String collectorEndpoint() {
        return "http://localhost:" + server.getPort();
    }

    /// A meter provider whose reader only exports when the dispatcher flushes it.
    private static SdkMeterProvider meterProvider(MetricExporter exporter) {
        return SdkMeterProvider.builder()
                .setResource(Resource.getDefault().merge(Resource.create(
                        Attributes.of(ResourceAttributes.SERVICE_NAME, "ride-dispatch"))))
                .registerMetricReader(PeriodicMetricReader.builder(exporter)
                        .setInterval(Duration.ofMinutes(10))
                        .build())
                .build();
    }

    private static Map<String, Metric> metricsByName(ExportMetricsServiceRequest request) {
        return request.getResourceMetricsList().stream()
                .flatMap(resourceMetrics -> resourceMetrics.getScopeMetricsList().stream())
                .flatMap(scopeMetrics -> scopeMetrics.getMetricsList().stream())
                .collect(Collectors.toMap(Metric::getName, metric -> metric));
    }

    private static Map<String, Long> pointsByCity(List<NumberDataPoint> points) {
        return points.stream().collect(Collectors.toMap(
                point -> city(point.getAttributesList()), NumberDataPoint::getAsInt));
    }

    private static Map<String, HistogramDataPoint> histogramByCity(Metric metric) {
        return metric.getHistogram().getDataPointsList().stream().collect(Collectors.toMap(
                point -> city(point.getAttributesList()), point -> point));
    }

    private static String city(List<KeyValue> keyValues) {
        return attributes(keyValues).get(CITY.getKey());
    }

    private static Map<String, String> attributes(List<KeyValue> keyValues) {
        return keyValues.stream().collect(Collectors.toMap(
                KeyValue::getKey, keyValue -> keyValue.getValue().getStringValue()));
    }

    /// The application code under measurement: bookings, quote latency, rides on the road.
    private static final class Dispatcher {

        private final LongCounter booked;

        private final DoubleHistogram quoteDuration;

        private final LongUpDownCounter inProgress;

        Dispatcher(Meter meter, AtomicLong vehiclesOnline) {
            booked = meter.counterBuilder("rides.booked")
                    .setDescription("Rides booked by riders")
                    .setUnit("{rides}")
                    .build();
            quoteDuration = meter.histogramBuilder("rides.quote_duration")
                    .setDescription("Time to quote a fare")
                    .setUnit("ms")
                    .build();
            inProgress = meter.upDownCounterBuilder("rides.in_progress")
                    .setDescription("Rides currently on the road")
                    .setUnit("{rides}")
                    .build();
            meter.gaugeBuilder("rides.vehicles_online")
                    .ofLongs()
                    .setDescription("Vehicles accepting rides")
                    .setUnit("{vehicles}")
                    .buildWithCallback(measurement -> measurement.record(vehiclesOnline.get()));
        }

        void bookRide(String city, double quoteMillis) {
            Attributes attributes = Attributes.of(CITY, city);
            quoteDuration.record(quoteMillis, attributes);
            booked.add(1, attributes);
            inProgress.add(1, attributes);
        }

        void finishRide(String city) {
            inProgress.add(-1, Attributes.of(CITY, city));
        }
    }

    /// A metric exporter decorator that records each export outcome for the dispatcher's health page.
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

    /// An in-process OTLP metrics collector that records the requests and headers it receives.
    private static final class Collector extends MetricsServiceGrpc.MetricsServiceImplBase {

        private final List<ExportMetricsServiceRequest> requests = new CopyOnWriteArrayList<>();

        private final List<Metadata> headers = new CopyOnWriteArrayList<>();

        private final AtomicReference<Status> pendingRejection = new AtomicReference<>();

        void rejectNext(Status status) {
            pendingRejection.set(status);
        }

        ServerInterceptor headerRecorder() {
            return new ServerInterceptor() {
                @Override
                public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                        ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> next) {
                    headers.add(metadata);
                    return next.startCall(call, metadata);
                }
            };
        }

        @Override
        public void export(
                ExportMetricsServiceRequest request, StreamObserver<ExportMetricsServiceResponse> responseObserver) {
            Status rejection = pendingRejection.getAndSet(null);
            if (rejection != null) {
                responseObserver.onError(rejection.asRuntimeException());
                return;
            }
            requests.add(request);
            responseObserver.onNext(ExportMetricsServiceResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }
    }
}
