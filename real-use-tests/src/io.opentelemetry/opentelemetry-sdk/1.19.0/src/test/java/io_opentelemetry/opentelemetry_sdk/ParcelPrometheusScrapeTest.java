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
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.prometheus.PrometheusHttpServer;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.View;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the Prometheus pull exporter: a `PrometheusHttpServer` registered as the metric reader
 * of an `SdkMeterProvider` and scraped over HTTP in the text and OpenMetrics formats.
 *
 * A parcel sorting depot counts scanned parcels per conveyor, records parcel weights into a
 * histogram with operator-chosen buckets and reports the backlog from a callback. A Prometheus
 * server scrapes the depot's `/metrics` endpoint: it negotiates the exposition format, asks for
 * gzip, narrows a scrape to selected series with `name[]`, and probes the health endpoint. Weights
 * recorded while a sampled sort span is active surface as OpenMetrics exemplars carrying the trace.
 */
class ParcelPrometheusScrapeTest {

    private static final AttributeKey<String> CONVEYOR = AttributeKey.stringKey("conveyor");

    private static final String TEXT_FORMAT = "text/plain; version=0.0.4; charset=utf-8";

    private static final String OPEN_METRICS_FORMAT = "application/openmetrics-text; version=1.0.0; charset=utf-8";

    private final AtomicLong backlog = new AtomicLong();

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private PrometheusHttpServer prometheus;

    private SdkMeterProvider meterProvider;

    private SdkTracerProvider tracerProvider;

    private ObservableLongGauge backlogGauge;

    private URI baseUri;

    @BeforeEach
    void setUp() throws IOException {
        int port = freePort();
        baseUri = URI.create("http://127.0.0.1:" + port);
        prometheus = PrometheusHttpServer.builder().setHost("127.0.0.1").setPort(port).build();
        meterProvider = SdkMeterProvider.builder()
                .setResource(Resource.getDefault().merge(Resource.create(
                        Attributes.of(AttributeKey.stringKey("service.name"), "parcel-depot"))))
                .registerMetricReader(prometheus)
                .registerView(
                        InstrumentSelector.builder().setName("parcel.weight").build(),
                        View.builder()
                                .setAggregation(Aggregation.explicitBucketHistogram(Arrays.asList(1.0, 5.0, 20.0)))
                                .build())
                .build();
        tracerProvider = SdkTracerProvider.builder().build();
    }

    @AfterEach
    void tearDown() {
        backlogGauge.close();
        meterProvider.shutdown().join(10, TimeUnit.SECONDS);
        tracerProvider.shutdown().join(10, TimeUnit.SECONDS);
    }

    @Test
    void prometheusScrapesDepotMetricsInEveryNegotiatedFormat() throws Exception {
        Depot depot = new Depot(meterProvider.get("depot.sorting"), tracerProvider.get("depot.sorting"));
        backlogGauge = depot.registerBacklog(backlog);

        depot.scan("north", 0.4);
        depot.scan("north", 3.5);
        depot.scan("north", 12.0);
        depot.scan("south", 42.0);
        backlog.set(17);

        HttpResponse<byte[]> text = scrape("/metrics", "text/plain", null);
        assertThat(text.statusCode()).isEqualTo(200);
        assertThat(text.headers().firstValue("Content-Type")).hasValue(TEXT_FORMAT);
        List<String> textLines = lines(text.body());
        assertThat(textLines)
                .contains("# TYPE parcels_scanned_total counter")
                .contains("# HELP parcels_scanned_total Parcels read by a conveyor scanner")
                .anyMatch(line -> line.startsWith("parcels_scanned_total{conveyor=\"north\"} 3.0"))
                .anyMatch(line -> line.startsWith("parcels_scanned_total{conveyor=\"south\"} 1.0"))
                .contains("# TYPE parcel_weight histogram")
                .anyMatch(line -> line.startsWith("parcel_weight_bucket{conveyor=\"north\",le=\"1.0\"} 1.0"))
                .anyMatch(line -> line.startsWith("parcel_weight_bucket{conveyor=\"north\",le=\"5.0\"} 2.0"))
                .anyMatch(line -> line.startsWith("parcel_weight_bucket{conveyor=\"north\",le=\"20.0\"} 3.0"))
                .anyMatch(line -> line.startsWith("parcel_weight_bucket{conveyor=\"north\",le=\"+Inf\"} 3.0"))
                .anyMatch(line -> line.startsWith("parcel_weight_bucket{conveyor=\"south\",le=\"20.0\"} 0.0"))
                .anyMatch(line -> line.startsWith("parcel_weight_count{conveyor=\"north\"} 3.0"))
                .anyMatch(line -> line.startsWith("parcel_weight_sum{conveyor=\"north\"} 15.9"))
                .contains("# TYPE depot_backlog gauge")
                .anyMatch(line -> line.startsWith("depot_backlog 17.0"))
                .noneMatch(line -> line.contains("# EOF"));

        depot.scan("south", 2.0);
        backlog.set(9);
        HttpResponse<byte[]> open = scrape("/metrics", "application/openmetrics-text; version=1.0.0", null);
        assertThat(open.statusCode()).isEqualTo(200);
        assertThat(open.headers().firstValue("Content-Type")).hasValue(OPEN_METRICS_FORMAT);
        List<String> openLines = lines(open.body());
        assertThat(openLines.get(openLines.size() - 1)).isEqualTo("# EOF");
        assertThat(openLines)
                .contains("# TYPE parcels_scanned counter")
                .anyMatch(line -> line.startsWith("parcels_scanned_total{conveyor=\"south\"} 2.0"))
                .anyMatch(line -> line.startsWith("depot_backlog 9.0"))
                .anyMatch(line -> line.startsWith("parcel_weight_bucket{conveyor=\"south\",le=\"5.0\"} 1.0")
                        && line.contains("# {span_id=\"" + depot.lastSpanId + "\",trace_id=\""
                                + depot.lastTraceId + "\"} 2.0"));

        HttpResponse<byte[]> zipped = scrape("/metrics?name[]=depot_backlog", "text/plain", "gzip");
        assertThat(zipped.statusCode()).isEqualTo(200);
        assertThat(zipped.headers().firstValue("Content-Encoding")).hasValue("gzip");
        List<String> filteredLines = lines(gunzip(zipped.body()));
        assertThat(filteredLines)
                .anyMatch(line -> line.startsWith("depot_backlog 9.0"))
                .noneMatch(line -> line.startsWith("parcels_scanned"))
                .noneMatch(line -> line.startsWith("parcel_weight"));

        HttpResponse<byte[]> health = scrape("/-/healthy", "*/*", null);
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(new String(health.body(), StandardCharsets.UTF_8)).isEqualTo("Exporter is Healthy.");
    }

    private HttpResponse<byte[]> scrape(String path, String accept, String encoding)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", accept);
        if (encoding != null) {
            request.header("Accept-Encoding", encoding);
        }
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static List<String> lines(byte[] body) {
        return new String(body, StandardCharsets.UTF_8).lines().collect(Collectors.toList());
    }

    private static byte[] gunzip(byte[] body) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return in.readAllBytes();
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** The depot's sorting code, instrumented the way the application ships it. */
    static final class Depot {

        private final Meter meter;

        private final Tracer tracer;

        private final LongCounter scanned;

        private final DoubleHistogram weight;

        private String lastTraceId;

        private String lastSpanId;

        Depot(Meter meter, Tracer tracer) {
            this.meter = meter;
            this.tracer = tracer;
            this.scanned = meter.counterBuilder("parcels.scanned")
                    .setDescription("Parcels read by a conveyor scanner")
                    .setUnit("1")
                    .build();
            this.weight = meter.histogramBuilder("parcel.weight")
                    .setDescription("Weight of a sorted parcel")
                    .setUnit("kg")
                    .build();
        }

        ObservableLongGauge registerBacklog(AtomicLong backlog) {
            return meter.gaugeBuilder("depot.backlog")
                    .ofLongs()
                    .setDescription("Parcels waiting to be sorted")
                    .buildWithCallback(measurement -> measurement.record(backlog.get()));
        }

        void scan(String conveyor, double kilograms) {
            Span span = tracer.spanBuilder("sort parcel").startSpan();
            try (Scope ignored = span.makeCurrent()) {
                Attributes attributes = Attributes.of(CONVEYOR, conveyor);
                scanned.add(1, attributes);
                weight.record(kilograms, attributes);
                lastTraceId = span.getSpanContext().getTraceId();
                lastSpanId = span.getSpanContext().getSpanId();
            } finally {
                span.end();
            }
        }
    }
}
