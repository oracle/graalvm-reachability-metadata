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
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import okhttp3.Handshake;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * TLS-secured export over OTLP/HTTPS with `setTrustedCertificates` and mutual-TLS `setClientTls`.
 *
 * A payment gateway sends its audit traces and settlement metrics to a collector that only
 * accepts HTTPS, signed by the company's private certificate authority. The gateway trusts that
 * authority from PEM bytes, and for the locked-down audit collector also presents its own client
 * certificate. The collector here is a MockWebServer serving HTTPS, so the test asserts on the
 * negotiated handshake and the client identity a real collector would see, on the decoded OTLP
 * payloads, and on how the gateway observes a collector it cannot authenticate or that rejects it.
 */
public class PaymentAuditTlsExportTest {

    private static final long WAIT_SECONDS = 20;

    private static final AttributeKey<String> PAYMENT_ID = AttributeKey.stringKey("payment.id");

    private static final AttributeKey<String> CURRENCY = AttributeKey.stringKey("payment.currency");

    private static final HeldCertificate COMPANY_CA = new HeldCertificate.Builder()
            .commonName("Acme Payments Internal CA")
            .certificateAuthority(0)
            .build();

    private static final HeldCertificate COLLECTOR_CERTIFICATE = new HeldCertificate.Builder()
            .commonName("otel-collector.acme.internal")
            .addSubjectAlternativeName("localhost")
            .signedBy(COMPANY_CA)
            .build();

    /// RSA, because the 1.19.0 exporter reads client keys only as PKCS#8 RSA.
    private static final HeldCertificate GATEWAY_CERTIFICATE = new HeldCertificate.Builder()
            .commonName("payment-gateway")
            .rsa2048()
            .signedBy(COMPANY_CA)
            .build();

    private MockWebServer collector;

    @BeforeEach
    void startCollector() throws IOException {
        HandshakeCertificates collectorTls = new HandshakeCertificates.Builder()
                .heldCertificate(COLLECTOR_CERTIFICATE)
                .addTrustedCertificate(COMPANY_CA.certificate())
                .build();
        collector = new MockWebServer();
        collector.useHttps(collectorTls.sslSocketFactory(), false);
        collector.start();
    }

    @AfterEach
    void stopCollector() throws IOException {
        collector.shutdown();
    }

    @Test
    void exportsAuditSpansToACollectorSignedByTheCompanyCa() throws Exception {
        acceptTraces();
        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint("/v1/traces"))
                .setTrustedCertificates(pem(COMPANY_CA.certificatePem()))
                .addHeader("x-audit-stream", "payments")
                .setTimeout(Duration.ofSeconds(10))
                .build();
        SdkTracerProvider tracerProvider = tracerProvider(exporter);
        PaymentGateway gateway = new PaymentGateway(tracerProvider.get("payment-gateway"), unmetered());

        gateway.authorize("pay-1001", "EUR");
        assertThat(tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();

        RecordedRequest request = collector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getRequestUrl().scheme()).isEqualTo("https");
        assertThat(request.getPath()).isEqualTo("/v1/traces");
        assertThat(request.getHeader("x-audit-stream")).isEqualTo("payments");
        Handshake handshake = request.getHandshake();
        assertThat(handshake).isNotNull();
        assertThat(handshake.tlsVersion().javaName()).startsWith("TLSv1.");
        assertThat(handshake.peerCertificates()).isEmpty();

        ExportTraceServiceRequest export = ExportTraceServiceRequest.parseFrom(request.getBody().readByteArray());
        assertThat(attributes(export.getResourceSpans(0).getResource().getAttributesList()))
                .containsEntry(ResourceAttributes.SERVICE_NAME.getKey(), "payment-gateway");
        List<io.opentelemetry.proto.trace.v1.Span> spans = export.getResourceSpans(0).getScopeSpans(0).getSpansList();
        assertThat(spans).singleElement().satisfies(span -> {
            assertThat(span.getName()).isEqualTo("authorize-payment");
            assertThat(span.getKind()).isEqualTo(io.opentelemetry.proto.trace.v1.Span.SpanKind.SPAN_KIND_SERVER);
            assertThat(attributes(span.getAttributesList()))
                    .containsEntry(PAYMENT_ID.getKey(), "pay-1001")
                    .containsEntry(CURRENCY.getKey(), "EUR");
        });
        assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void presentsTheGatewayClientCertificateToAMutualTlsCollector() throws Exception {
        collector.requireClientAuth();
        acceptTraces();
        acceptTraces();
        acceptTraces();
        acceptMetrics();
        OtlpHttpSpanExporter spanExporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint("/v1/traces"))
                .setTrustedCertificates(pem(COMPANY_CA.certificatePem()))
                .setClientTls(pem(GATEWAY_CERTIFICATE.privateKeyPkcs8Pem()), pem(GATEWAY_CERTIFICATE.certificatePem()))
                .build();
        OtlpHttpMetricExporter metricExporter = OtlpHttpMetricExporter.builder()
                .setEndpoint(endpoint("/v1/metrics"))
                .setTrustedCertificates(pem(COMPANY_CA.certificatePem()))
                .setClientTls(pem(GATEWAY_CERTIFICATE.privateKeyPkcs8Pem()), pem(GATEWAY_CERTIFICATE.certificatePem()))
                .build();
        SdkTracerProvider tracerProvider = tracerProvider(spanExporter);
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(gatewayResource())
                .registerMetricReader(PeriodicMetricReader.builder(metricExporter)
                        .setInterval(Duration.ofMinutes(10))
                        .build())
                .build();
        PaymentGateway gateway =
                new PaymentGateway(tracerProvider.get("payment-gateway"), meterProvider.get("payment-gateway"));

        gateway.authorize("pay-2001", "USD");
        gateway.authorize("pay-2002", "USD");
        gateway.authorize("pay-2003", "CHF");
        assertThat(tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(meterProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();

        List<RecordedRequest> requests = List.of(
                collector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS),
                collector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS),
                collector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS),
                collector.takeRequest(WAIT_SECONDS, TimeUnit.SECONDS));
        assertThat(requests).doesNotContainNull();
        for (RecordedRequest request : requests) {
            assertThat(request.getHandshake()).isNotNull();
            assertThat(request.getHandshake().peerPrincipal()).isNotNull();
            assertThat(request.getHandshake().peerPrincipal().getName()).isEqualTo("CN=payment-gateway");
        }
        Map<String, List<RecordedRequest>> byPath =
                requests.stream().collect(Collectors.groupingBy(RecordedRequest::getPath));
        assertThat(byPath).containsOnlyKeys("/v1/traces", "/v1/metrics");
        assertThat(byPath.get("/v1/traces")).hasSize(3);

        RecordedRequest metricsRequest = byPath.get("/v1/metrics").get(0);
        ExportMetricsServiceRequest metrics =
                ExportMetricsServiceRequest.parseFrom(metricsRequest.getBody().readByteArray());
        Metric settled = metrics.getResourceMetrics(0).getScopeMetrics(0).getMetricsList().stream()
                .filter(metric -> metric.getName().equals("payments.settled"))
                .findFirst()
                .orElseThrow();
        Map<String, Long> settledByCurrency = settled.getSum().getDataPointsList().stream()
                .collect(Collectors.toMap(
                        point -> attributes(point.getAttributesList()).get(CURRENCY.getKey()),
                        NumberDataPoint::getAsInt));
        assertThat(settledByCurrency).containsOnly(Map.entry("USD", 2L), Map.entry("CHF", 1L));

        assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(meterProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void refusesACollectorWhoseCertificateIsNotSignedByATrustedCa() throws Exception {
        HeldCertificate otherCa = new HeldCertificate.Builder()
                .commonName("Unrelated Public CA")
                .certificateAuthority(0)
                .build();
        ExportOutcomes outcomes = new ExportOutcomes(OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint("/v1/traces"))
                .setTrustedCertificates(pem(otherCa.certificatePem()))
                .build());
        SdkTracerProvider tracerProvider = tracerProvider(outcomes);
        PaymentGateway gateway = new PaymentGateway(tracerProvider.get("payment-gateway"), unmetered());

        gateway.authorize("pay-3001", "GBP");
        tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(outcomes.results).containsExactly(false);
        assertThat(collector.getRequestCount()).isZero();
        assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void reportsAMutualTlsCollectorThatRejectsAGatewayWithoutAClientCertificate() throws Exception {
        collector.requireClientAuth();
        acceptTraces();
        ExportOutcomes outcomes = new ExportOutcomes(OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint("/v1/traces"))
                .setTrustedCertificates(pem(COMPANY_CA.certificatePem()))
                .build());
        SdkTracerProvider tracerProvider = tracerProvider(outcomes);
        PaymentGateway gateway = new PaymentGateway(tracerProvider.get("payment-gateway"), unmetered());

        gateway.authorize("pay-4001", "JPY");
        tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(outcomes.results).containsExactly(false);
        assertThat(collector.getRequestCount()).isZero();
        assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    private void acceptTraces() {
        collector.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/x-protobuf")
                .setBody(new Buffer().write(ExportTraceServiceResponse.getDefaultInstance().toByteArray())));
    }

    private void acceptMetrics() {
        collector.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/x-protobuf")
                .setBody(new Buffer().write(ExportMetricsServiceResponse.getDefaultInstance().toByteArray())));
    }

    /// The collector's HTTPS URL on the host name its certificate is issued for.
    private String endpoint(String path) {
        return "https://localhost:" + collector.getPort() + path;
    }

    /// A meter for scenarios that only export traces.
    private static Meter unmetered() {
        return MeterProvider.noop().get("payment-gateway");
    }

    private static byte[] pem(String pem) {
        return pem.getBytes(StandardCharsets.US_ASCII);
    }

    private static Resource gatewayResource() {
        return Resource.getDefault().merge(Resource.create(Attributes.of(
                ResourceAttributes.SERVICE_NAME, "payment-gateway")));
    }

    private static SdkTracerProvider tracerProvider(SpanExporter exporter) {
        return SdkTracerProvider.builder()
                .setResource(gatewayResource())
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
    }

    private static Map<String, String> attributes(List<KeyValue> keyValues) {
        return keyValues.stream()
                .collect(Collectors.toMap(KeyValue::getKey, keyValue -> keyValue.getValue().getStringValue()));
    }

    /// The application code under audit: one server span per authorization, one settlement count.
    private static final class PaymentGateway {

        private final Tracer tracer;

        private final LongCounter settled;

        PaymentGateway(Tracer tracer, Meter meter) {
            this.tracer = tracer;
            this.settled = meter.counterBuilder("payments.settled")
                    .setDescription("Payments settled by the gateway")
                    .setUnit("{payment}")
                    .build();
        }

        void authorize(String paymentId, String currency) {
            Span span = tracer.spanBuilder("authorize-payment")
                    .setSpanKind(SpanKind.SERVER)
                    .setAttribute(PAYMENT_ID, paymentId)
                    .setAttribute(CURRENCY, currency)
                    .startSpan();
            try {
                settled.add(1, Attributes.of(CURRENCY, currency));
            } finally {
                span.end();
            }
        }
    }

    /// A span exporter decorator that records each export outcome for the gateway's health page.
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
