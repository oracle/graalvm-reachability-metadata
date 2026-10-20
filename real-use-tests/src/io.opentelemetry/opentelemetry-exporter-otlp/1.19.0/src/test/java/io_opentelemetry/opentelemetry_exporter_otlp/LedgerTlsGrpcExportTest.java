/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_exporter_otlp;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.HistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.Metric;
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
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import okhttp3.tls.HeldCertificate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * TLS-secured export over OTLP/gRPC on the default OkHttp transport, with `setTrustedCertificates`
 * and mutual-TLS `setClientTls` on `OtlpGrpcSpanExporter` and `OtlpGrpcMetricExporter`.
 *
 * A bank's ledger service ships its posting traces and posting-amount histograms to a collector
 * that only listens on gRPC over TLS, with a certificate issued by the bank's private authority.
 * The ledger trusts that authority from PEM bytes and, for the regulated collector, also presents
 * its own client certificate. The collector here is an in-process grpc-netty server with a TLS
 * listener, so the test asserts on the TLS session and client identity a real collector would
 * see, on the decoded OTLP requests, and on how the ledger observes a collector it cannot
 * authenticate or that refuses to authenticate it.
 */
public class LedgerTlsGrpcExportTest {

    private static final long WAIT_SECONDS = 20;

    private static final Metadata.Key<String> TENANT =
            Metadata.Key.of("x-ledger-tenant", Metadata.ASCII_STRING_MARSHALLER);

    private static final AttributeKey<String> ACCOUNT = AttributeKey.stringKey("ledger.account");

    private static final AttributeKey<String> ENTRY_TYPE = AttributeKey.stringKey("ledger.entry_type");

    private static final HeldCertificate BANK_CA = new HeldCertificate.Builder()
            .commonName("Northbank Internal CA")
            .certificateAuthority(0)
            .build();

    private static final HeldCertificate COLLECTOR_CERTIFICATE = new HeldCertificate.Builder()
            .commonName("otel-collector.northbank.internal")
            .addSubjectAlternativeName("localhost")
            .signedBy(BANK_CA)
            .build();

    /// RSA, because the 1.19.0 exporter reads client keys only as PKCS#8 RSA.
    private static final HeldCertificate LEDGER_CERTIFICATE = new HeldCertificate.Builder()
            .commonName("ledger-service")
            .rsa2048()
            .signedBy(BANK_CA)
            .build();

    private Collector collector;

    private Server server;

    @AfterEach
    void stopCollector() throws InterruptedException {
        if (server != null) {
            server.shutdownNow();
            server.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void exportsPostingSpansToACollectorSignedByTheBankCa() throws Exception {
        startCollector(ClientAuth.NONE);
        OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpoint())
                .setTrustedCertificates(pem(BANK_CA.certificatePem()))
                .addHeader(TENANT.name(), "retail")
                .setTimeout(Duration.ofSeconds(10))
                .build();
        SdkTracerProvider tracerProvider = tracerProvider(exporter);
        LedgerService ledger = new LedgerService(tracerProvider.get("ledger-service"), unmetered());

        ledger.post("acct-1001", "debit", 12.5);
        assertThat(tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();

        assertThat(collector.calls).singleElement().satisfies(call -> {
            assertThat(call.method).isEqualTo("opentelemetry.proto.collector.trace.v1.TraceService/Export");
            assertThat(call.tlsProtocol).startsWith("TLSv1.");
            assertThat(call.clientPrincipal).isNull();
            assertThat(call.tenant).isEqualTo("retail");
        });
        assertThat(collector.traces.requests).singleElement().satisfies(export -> {
            assertThat(attributes(export.getResourceSpans(0).getResource().getAttributesList()))
                    .containsEntry(ResourceAttributes.SERVICE_NAME.getKey(), "ledger");
            assertThat(export.getResourceSpans(0).getScopeSpans(0).getSpansList()).singleElement()
                    .satisfies(span -> {
                        assertThat(span.getName()).isEqualTo("post-entry");
                        assertThat(span.getKind())
                                .isEqualTo(io.opentelemetry.proto.trace.v1.Span.SpanKind.SPAN_KIND_INTERNAL);
                        assertThat(attributes(span.getAttributesList()))
                                .containsEntry(ACCOUNT.getKey(), "acct-1001")
                                .containsEntry(ENTRY_TYPE.getKey(), "debit");
                    });
        });
        assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void presentsTheLedgerClientCertificateToAMutualTlsCollector() throws Exception {
        startCollector(ClientAuth.REQUIRE);
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpoint())
                .setTrustedCertificates(pem(BANK_CA.certificatePem()))
                .setClientTls(pem(LEDGER_CERTIFICATE.privateKeyPkcs8Pem()), pem(LEDGER_CERTIFICATE.certificatePem()))
                .build();
        OtlpGrpcMetricExporter metricExporter = OtlpGrpcMetricExporter.builder()
                .setEndpoint(endpoint())
                .setTrustedCertificates(pem(BANK_CA.certificatePem()))
                .setClientTls(pem(LEDGER_CERTIFICATE.privateKeyPkcs8Pem()), pem(LEDGER_CERTIFICATE.certificatePem()))
                .build();
        SdkTracerProvider tracerProvider = tracerProvider(spanExporter);
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(ledgerResource())
                .registerMetricReader(PeriodicMetricReader.builder(metricExporter)
                        .setInterval(Duration.ofMinutes(10))
                        .build())
                .build();
        LedgerService ledger =
                new LedgerService(tracerProvider.get("ledger-service"), meterProvider.get("ledger-service"));

        ledger.post("acct-2001", "credit", 40.0);
        ledger.post("acct-2002", "debit", 7.0);
        ledger.post("acct-2003", "credit", 90.0);
        assertThat(tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(meterProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();

        assertThat(collector.calls).hasSize(4).allSatisfy(call -> {
            assertThat(call.tlsProtocol).startsWith("TLSv1.");
            assertThat(call.clientPrincipal).isEqualTo("CN=ledger-service");
        });
        Map<String, Long> callsByMethod = collector.calls.stream()
                .collect(Collectors.groupingBy(call -> call.method, Collectors.counting()));
        assertThat(callsByMethod).containsOnly(
                Map.entry("opentelemetry.proto.collector.trace.v1.TraceService/Export", 3L),
                Map.entry("opentelemetry.proto.collector.metrics.v1.MetricsService/Export", 1L));

        List<String> postedAccounts = collector.traces.requests.stream()
                .flatMap(export -> export.getResourceSpans(0).getScopeSpans(0).getSpansList().stream())
                .map(span -> attributes(span.getAttributesList()).get(ACCOUNT.getKey()))
                .collect(Collectors.toList());
        assertThat(postedAccounts).containsExactlyInAnyOrder("acct-2001", "acct-2002", "acct-2003");

        ExportMetricsServiceRequest metrics = collector.metrics.requests.get(0);
        assertThat(attributes(metrics.getResourceMetrics(0).getResource().getAttributesList()))
                .containsEntry(ResourceAttributes.SERVICE_NAME.getKey(), "ledger");
        Metric postedAmounts = metrics.getResourceMetrics(0).getScopeMetrics(0).getMetricsList().stream()
                .filter(metric -> metric.getName().equals("ledger.posting.amount"))
                .findFirst()
                .orElseThrow();
        assertThat(postedAmounts.getUnit()).isEqualTo("EUR");
        Map<String, HistogramDataPoint> byEntryType = postedAmounts.getHistogram().getDataPointsList().stream()
                .collect(Collectors.toMap(
                        point -> attributes(point.getAttributesList()).get(ENTRY_TYPE.getKey()), point -> point));
        assertThat(byEntryType).containsOnlyKeys("credit", "debit");
        assertThat(byEntryType.get("credit").getCount()).isEqualTo(2L);
        assertThat(byEntryType.get("credit").getSum()).isEqualTo(130.0);
        assertThat(byEntryType.get("debit").getCount()).isEqualTo(1L);
        assertThat(byEntryType.get("debit").getSum()).isEqualTo(7.0);

        assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(meterProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void refusesACollectorWhoseCertificateIsNotSignedByATrustedCa() throws Exception {
        startCollector(ClientAuth.NONE);
        HeldCertificate otherCa = new HeldCertificate.Builder()
                .commonName("Unrelated Public CA")
                .certificateAuthority(0)
                .build();
        ExportOutcomes outcomes = new ExportOutcomes(OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpoint())
                .setTrustedCertificates(pem(otherCa.certificatePem()))
                .setTimeout(Duration.ofSeconds(10))
                .build());
        SdkTracerProvider tracerProvider = tracerProvider(outcomes);
        LedgerService ledger = new LedgerService(tracerProvider.get("ledger-service"), unmetered());

        ledger.post("acct-3001", "debit", 3.0);
        tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(outcomes.results).containsExactly(false);
        assertThat(collector.calls).isEmpty();
        assertThat(collector.traces.requests).isEmpty();
        assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void reportsAMutualTlsCollectorThatRejectsALedgerWithoutAClientCertificate() throws Exception {
        startCollector(ClientAuth.REQUIRE);
        ExportOutcomes outcomes = new ExportOutcomes(OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpoint())
                .setTrustedCertificates(pem(BANK_CA.certificatePem()))
                .setTimeout(Duration.ofSeconds(10))
                .build());
        SdkTracerProvider tracerProvider = tracerProvider(outcomes);
        LedgerService ledger = new LedgerService(tracerProvider.get("ledger-service"), unmetered());

        ledger.post("acct-4001", "credit", 5.0);
        tracerProvider.forceFlush().join(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(outcomes.results).containsExactly(false);
        assertThat(collector.calls).isEmpty();
        assertThat(collector.traces.requests).isEmpty();
        assertThat(tracerProvider.shutdown().join(WAIT_SECONDS, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    /// Starts the collector's TLS listener with the JDK TLS stack and the given client policy.
    private void startCollector(ClientAuth clientAuth) throws IOException {
        SslContext tls = GrpcSslContexts.configure(
                        SslContextBuilder.forServer(
                                COLLECTOR_CERTIFICATE.keyPair().getPrivate(), COLLECTOR_CERTIFICATE.certificate()),
                        SslProvider.JDK)
                .trustManager(BANK_CA.certificate())
                .clientAuth(clientAuth)
                .build();
        collector = new Collector();
        server = NettyServerBuilder.forPort(0)
                .sslContext(tls)
                .addService(ServerInterceptors.intercept(collector.traces, collector.callRecorder()))
                .addService(ServerInterceptors.intercept(collector.metrics, collector.callRecorder()))
                .build()
                .start();
    }

    /// The collector's TLS endpoint on the host name its certificate is issued for.
    private String endpoint() {
        return "https://localhost:" + server.getPort();
    }

    /// A meter for scenarios that only export traces.
    private static Meter unmetered() {
        return MeterProvider.noop().get("ledger-service");
    }

    private static byte[] pem(String pem) {
        return pem.getBytes(StandardCharsets.US_ASCII);
    }

    private static Resource ledgerResource() {
        return Resource.getDefault().merge(Resource.create(Attributes.of(
                ResourceAttributes.SERVICE_NAME, "ledger")));
    }

    private static SdkTracerProvider tracerProvider(SpanExporter exporter) {
        return SdkTracerProvider.builder()
                .setResource(ledgerResource())
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
    }

    private static Map<String, String> attributes(List<KeyValue> keyValues) {
        return keyValues.stream()
                .collect(Collectors.toMap(KeyValue::getKey, keyValue -> keyValue.getValue().getStringValue()));
    }

    /// The application code under observation: one span and one amount sample per posting.
    private static final class LedgerService {

        private final Tracer tracer;

        private final DoubleHistogram amounts;

        LedgerService(Tracer tracer, Meter meter) {
            this.tracer = tracer;
            this.amounts = meter.histogramBuilder("ledger.posting.amount")
                    .setDescription("Amounts posted to the ledger")
                    .setUnit("EUR")
                    .build();
        }

        void post(String account, String entryType, double amount) {
            Span span = tracer.spanBuilder("post-entry")
                    .setSpanKind(SpanKind.INTERNAL)
                    .setAttribute(ACCOUNT, account)
                    .setAttribute(ENTRY_TYPE, entryType)
                    .startSpan();
            try {
                amounts.record(amount, Attributes.of(ENTRY_TYPE, entryType));
            } finally {
                span.end();
            }
        }
    }

    /// A span exporter decorator that records each export outcome for the ledger's health page.
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

    /// One gRPC call as the collector saw it: the method, the TLS session, and the caller's identity.
    private static final class CallRecord {

        private final String method;

        private final String tlsProtocol;

        private final String clientPrincipal;

        private final String tenant;

        CallRecord(String method, String tlsProtocol, String clientPrincipal, String tenant) {
            this.method = method;
            this.tlsProtocol = tlsProtocol;
            this.clientPrincipal = clientPrincipal;
            this.tenant = tenant;
        }
    }

    /// An in-process OTLP collector serving the trace and metrics services over TLS.
    private static final class Collector {

        private final TraceService traces = new TraceService();

        private final MetricsService metrics = new MetricsService();

        private final List<CallRecord> calls = new CopyOnWriteArrayList<>();

        ServerInterceptor callRecorder() {
            return new ServerInterceptor() {
                @Override
                public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                        ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> next) {
                    SSLSession session = call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION);
                    calls.add(new CallRecord(
                            call.getMethodDescriptor().getFullMethodName(),
                            session == null ? null : session.getProtocol(),
                            clientPrincipal(session),
                            metadata.get(TENANT)));
                    return next.startCall(call, metadata);
                }
            };
        }

        private static String clientPrincipal(SSLSession session) {
            if (session == null) {
                return null;
            }
            try {
                return session.getPeerPrincipal().getName();
            } catch (SSLPeerUnverifiedException noClientCertificate) {
                return null;
            }
        }
    }

    private static final class TraceService extends TraceServiceGrpc.TraceServiceImplBase {

        private final List<ExportTraceServiceRequest> requests = new CopyOnWriteArrayList<>();

        @Override
        public void export(
                ExportTraceServiceRequest request, StreamObserver<ExportTraceServiceResponse> responseObserver) {
            requests.add(request);
            responseObserver.onNext(ExportTraceServiceResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }
    }

    private static final class MetricsService extends MetricsServiceGrpc.MetricsServiceImplBase {

        private final List<ExportMetricsServiceRequest> requests = new CopyOnWriteArrayList<>();

        @Override
        public void export(
                ExportMetricsServiceRequest request, StreamObserver<ExportMetricsServiceResponse> responseObserver) {
            requests.add(request);
            responseObserver.onNext(ExportMetricsServiceResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }
    }
}
