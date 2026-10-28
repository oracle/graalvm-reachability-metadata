/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.View;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the metrics SDK: an `SdkMeterProvider` whose views rename, re-bucket, filter and drop
 * instruments, aggregating synchronous and asynchronous instruments for cumulative and delta readers.
 *
 * A ticket booking service counts requests per route, records how long each booking took,
 * tracks bookings in flight and reports the depth of its waiting queue from a callback. The
 * operator registers views that keep only the route and method attributes on the request
 * counter (dropping the high-cardinality customer id), give the latency histogram service-level
 * buckets under a new name, and discard a debug instrument. One reader collects cumulative
 * totals for a dashboard while a second collects deltas, as a push exporter would.
 */
class BookingMetricsTest {

    private static final String SCOPE_NAME = "tickets.booking";

    private static final String SCOPE_VERSION = "3.1.0";

    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");

    private static final AttributeKey<String> ROUTE = AttributeKey.stringKey("http.route");

    private static final AttributeKey<String> METHOD = AttributeKey.stringKey("http.method");

    private static final AttributeKey<String> CUSTOMER = AttributeKey.stringKey("customer.id");

    private static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("booking.outcome");

    private InMemoryMetricReader cumulativeReader;

    private InMemoryMetricReader deltaReader;

    private SdkMeterProvider meterProvider;

    @BeforeEach
    void setUp() {
        cumulativeReader = InMemoryMetricReader.create();
        deltaReader = InMemoryMetricReader.createDelta();
        meterProvider = SdkMeterProvider.builder()
                .setResource(Resource.getDefault().merge(
                        Resource.create(Attributes.of(SERVICE_NAME, "booking-service"))))
                .registerMetricReader(cumulativeReader)
                .registerMetricReader(deltaReader)
                .registerView(
                        InstrumentSelector.builder()
                                .setType(InstrumentType.COUNTER)
                                .setName("bookings.requests")
                                .build(),
                        View.builder()
                                .setAttributeFilter(key -> key.equals(ROUTE.getKey()) || key.equals(METHOD.getKey()))
                                .build())
                .registerView(
                        InstrumentSelector.builder()
                                .setType(InstrumentType.HISTOGRAM)
                                .setMeterName(SCOPE_NAME)
                                .setName("bookings.duration")
                                .build(),
                        View.builder()
                                .setName("bookings.latency")
                                .setDescription("Booking latency against the service-level buckets")
                                .setAggregation(Aggregation.explicitBucketHistogram(List.of(50.0, 200.0, 1000.0)))
                                .build())
                .registerView(
                        InstrumentSelector.builder().setName("bookings.debug.cache_probes").build(),
                        View.builder().setAggregation(Aggregation.drop()).build())
                .build();
    }

    @AfterEach
    void tearDown() {
        CompletableResultCode shutdown = meterProvider.shutdown().join(10, TimeUnit.SECONDS);
        assertThat(shutdown.isSuccess()).isTrue();
    }

    @Test
    void viewsShapeWhatTheCumulativeReaderCollects() {
        BookingService service = new BookingService(meterProvider.meterBuilder(SCOPE_NAME)
                .setInstrumentationVersion(SCOPE_VERSION)
                .build());
        try (ObservableLongGauge ignored = service.registerQueueGauge()) {
            service.enqueue("customer-1");
            service.enqueue("customer-2");
            service.enqueue("customer-3");
            service.book("/concerts/{id}", "POST", "customer-1", 30.0, true);
            service.book("/concerts/{id}", "POST", "customer-2", 120.0, true);
            service.book("/concerts/{id}", "POST", "customer-3", 1500.0, false);
            service.book("/theatres/{id}", "GET", "customer-1", 10.0, true);
            service.startBooking();
            service.startBooking();
            service.finishBooking();

            Map<String, MetricData> metrics = byName(cumulativeReader.collectAllMetrics());

            assertThat(metrics).containsOnlyKeys(
                    "bookings.requests", "bookings.latency", "bookings.in_flight", "bookings.queue.depth");
            assertThat(metrics.values()).allSatisfy(metric -> {
                assertThat(metric.getResource().getAttribute(SERVICE_NAME)).isEqualTo("booking-service");
                assertThat(metric.getInstrumentationScopeInfo().getName()).isEqualTo(SCOPE_NAME);
                assertThat(metric.getInstrumentationScopeInfo().getVersion()).isEqualTo(SCOPE_VERSION);
            });

            MetricData requests = metrics.get("bookings.requests");
            assertThat(requests.getType()).isEqualTo(MetricDataType.LONG_SUM);
            assertThat(requests.getUnit()).isEqualTo("{request}");
            assertThat(requests.getLongSumData().isMonotonic()).isTrue();
            assertThat(requests.getLongSumData().getAggregationTemporality())
                    .isEqualTo(AggregationTemporality.CUMULATIVE);
            Map<Attributes, Long> requestCounts = requests.getLongSumData().getPoints().stream()
                    .collect(Collectors.toMap(LongPointData::getAttributes, LongPointData::getValue));
            assertThat(requestCounts).containsOnly(
                    Map.entry(Attributes.of(ROUTE, "/concerts/{id}", METHOD, "POST"), 3L),
                    Map.entry(Attributes.of(ROUTE, "/theatres/{id}", METHOD, "GET"), 1L));

            MetricData latency = metrics.get("bookings.latency");
            assertThat(latency.getType()).isEqualTo(MetricDataType.HISTOGRAM);
            assertThat(latency.getDescription()).isEqualTo("Booking latency against the service-level buckets");
            assertThat(latency.getUnit()).isEqualTo("ms");
            Map<Attributes, HistogramPointData> latencyPoints = latency.getHistogramData().getPoints().stream()
                    .collect(Collectors.toMap(HistogramPointData::getAttributes, Function.identity()));
            HistogramPointData confirmed = latencyPoints.get(Attributes.of(OUTCOME, "confirmed"));
            assertThat(confirmed.getBoundaries()).containsExactly(50.0, 200.0, 1000.0);
            assertThat(confirmed.getCounts()).containsExactly(2L, 1L, 0L, 0L);
            assertThat(confirmed.getCount()).isEqualTo(3L);
            assertThat(confirmed.getSum()).isEqualTo(160.0);
            assertThat(confirmed.getMin()).isEqualTo(10.0);
            assertThat(confirmed.getMax()).isEqualTo(120.0);
            HistogramPointData soldOut = latencyPoints.get(Attributes.of(OUTCOME, "sold_out"));
            assertThat(soldOut.getCounts()).containsExactly(0L, 0L, 0L, 1L);
            assertThat(soldOut.getSum()).isEqualTo(1500.0);

            MetricData inFlight = metrics.get("bookings.in_flight");
            assertThat(inFlight.getLongSumData().isMonotonic()).isFalse();
            assertThat(inFlight.getLongSumData().getPoints())
                    .singleElement()
                    .extracting(LongPointData::getValue)
                    .isEqualTo(1L);

            MetricData queueDepth = metrics.get("bookings.queue.depth");
            assertThat(queueDepth.getType()).isEqualTo(MetricDataType.LONG_GAUGE);
            assertThat(queueDepth.getLongGaugeData().getPoints())
                    .singleElement()
                    .extracting(LongPointData::getValue)
                    .isEqualTo(0L);

            service.enqueue("customer-4");
            service.enqueue("customer-5");
            Map<String, MetricData> next = byName(cumulativeReader.collectAllMetrics());
            assertThat(next.get("bookings.queue.depth").getLongGaugeData().getPoints())
                    .singleElement()
                    .extracting(LongPointData::getValue)
                    .isEqualTo(2L);
            assertThat(next.get("bookings.requests").getLongSumData().getPoints())
                    .extracting(LongPointData::getValue)
                    .containsExactlyInAnyOrder(3L, 1L);
        }
    }

    @Test
    void deltaReaderReportsOnlyWhatHappenedSinceItsLastCollection() {
        BookingService service = new BookingService(meterProvider.get(SCOPE_NAME));
        Attributes concertsPost = Attributes.of(ROUTE, "/concerts/{id}", METHOD, "POST");

        service.book("/concerts/{id}", "POST", "customer-1", 40.0, true);
        service.book("/concerts/{id}", "POST", "customer-2", 60.0, true);
        MetricData firstRequests = byName(deltaReader.collectAllMetrics()).get("bookings.requests");
        assertThat(firstRequests.getLongSumData().getAggregationTemporality())
                .isEqualTo(AggregationTemporality.DELTA);
        assertThat(firstRequests.getLongSumData().getPoints())
                .singleElement()
                .satisfies(point -> {
                    assertThat(point.getAttributes()).isEqualTo(concertsPost);
                    assertThat(point.getValue()).isEqualTo(2L);
                });

        service.book("/concerts/{id}", "POST", "customer-3", 70.0, true);
        Map<String, MetricData> second = byName(deltaReader.collectAllMetrics());
        LongPointData secondPoint = second.get("bookings.requests").getLongSumData().getPoints().stream()
                .filter(point -> point.getAttributes().equals(concertsPost))
                .findFirst()
                .orElseThrow();
        assertThat(secondPoint.getValue()).isEqualTo(1L);
        assertThat(secondPoint.getStartEpochNanos())
                .isGreaterThanOrEqualTo(firstRequests.getLongSumData().getPoints().iterator().next().getEpochNanos());
        HistogramPointData secondLatency = second.get("bookings.latency").getHistogramData().getPoints().stream()
                .filter(point -> point.getCount() > 0)
                .findFirst()
                .orElseThrow();
        assertThat(secondLatency.getCount()).isEqualTo(1L);
        assertThat(secondLatency.getSum()).isEqualTo(70.0);

        MetricData cumulativeRequests = byName(cumulativeReader.collectAllMetrics()).get("bookings.requests");
        assertThat(cumulativeRequests.getLongSumData().getPoints())
                .singleElement()
                .extracting(LongPointData::getValue)
                .isEqualTo(3L);
    }

    private static Map<String, MetricData> byName(Collection<MetricData> metrics) {
        return metrics.stream().collect(Collectors.toMap(MetricData::getName, Function.identity()));
    }

    /** Ticket booking service instrumented with the OpenTelemetry metrics API. */
    static final class BookingService {

        private final Meter meter;

        private final LongCounter requests;

        private final DoubleHistogram duration;

        private final LongUpDownCounter inFlight;

        private final LongCounter cacheProbes;

        private final Deque<String> waitingQueue = new ArrayDeque<>();

        BookingService(Meter meter) {
            this.meter = meter;
            this.requests = meter.counterBuilder("bookings.requests")
                    .setDescription("Booking requests received")
                    .setUnit("{request}")
                    .build();
            this.duration = meter.histogramBuilder("bookings.duration")
                    .setDescription("Time taken to complete a booking")
                    .setUnit("ms")
                    .build();
            this.inFlight = meter.upDownCounterBuilder("bookings.in_flight")
                    .setDescription("Bookings currently being processed")
                    .build();
            this.cacheProbes = meter.counterBuilder("bookings.debug.cache_probes").build();
        }

        ObservableLongGauge registerQueueGauge() {
            return meter.gaugeBuilder("bookings.queue.depth")
                    .setDescription("Customers waiting for a booking slot")
                    .ofLongs()
                    .buildWithCallback(measurement -> {
                        synchronized (waitingQueue) {
                            measurement.record(waitingQueue.size());
                        }
                    });
        }

        void enqueue(String customerId) {
            synchronized (waitingQueue) {
                waitingQueue.addLast(customerId);
            }
        }

        void book(String route, String method, String customerId, double millis, boolean seatsLeft) {
            synchronized (waitingQueue) {
                waitingQueue.remove(customerId);
            }
            cacheProbes.add(1);
            requests.add(1, Attributes.of(ROUTE, route, METHOD, method, CUSTOMER, customerId));
            duration.record(millis, Attributes.of(OUTCOME, seatsLeft ? "confirmed" : "sold_out"));
        }

        void startBooking() {
            inFlight.add(1);
        }

        void finishBooking() {
            inFlight.add(-1);
        }
    }
}
