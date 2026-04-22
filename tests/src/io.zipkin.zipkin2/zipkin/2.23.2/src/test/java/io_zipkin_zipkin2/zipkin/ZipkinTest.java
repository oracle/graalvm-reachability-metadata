/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_zipkin_zipkin2.zipkin;

import org.junit.jupiter.api.Test;
import zipkin2.Annotation;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.DependencyLink;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.SpanBytesDecoderDetector;
import zipkin2.codec.DependencyLinkBytesDecoder;
import zipkin2.codec.DependencyLinkBytesEncoder;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.storage.InMemoryStorage;
import zipkin2.storage.QueryRequest;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class ZipkinTest {

    private static final String TRACE_ID = "463ac35c9f6413ad48485a3953bb6124";
    private static final String PARENT_ID = "463ac35c9f6413ad";
    private static final String CLIENT_SPAN_ID = "a2fb4a1d1a96d312";
    private static final String SERVER_SPAN_ID = "b7ad6b7169203331";
    private static final String PRODUCER_TRACE_ID = "463ac35c9f6413ad48485a3953bb6125";
    private static final String PRODUCER_SPAN_ID = "a2fb4a1d1a96d313";
    private static final String SHORT_TRACE_ID = "48485a3953bb6124";
    private static final String SHORT_TRACE_SPAN_ID = "b7ad6b7169203332";

    @Test
    void buildsSpanModelsAndSupportsToBuilder() {
        Endpoint frontend = endpoint("Frontend", "192.168.0.1", 8080);
        Endpoint backend = endpoint("Backend", "2001:db8::1", 9000);
        Span clientSpan = frontendClientSpan(frontend, backend);

        assertThat(frontend.serviceName()).isEqualTo("frontend");
        assertThat(frontend.ipv4()).isEqualTo("192.168.0.1");
        assertThat(frontend.port()).isEqualTo(8080);

        assertThat(backend.serviceName()).isEqualTo("backend");
        assertThat(backend.ipv6()).isEqualTo("2001:db8::1");
        assertThat(backend.ipv6Bytes()).hasSize(16);

        assertThat(clientSpan.traceId()).isEqualTo(TRACE_ID);
        assertThat(clientSpan.parentId()).isEqualTo(PARENT_ID);
        assertThat(clientSpan.id()).isEqualTo(CLIENT_SPAN_ID);
        assertThat(clientSpan.name()).isEqualTo("get");
        assertThat(clientSpan.localServiceName()).isEqualTo("frontend");
        assertThat(clientSpan.remoteServiceName()).isEqualTo("backend");
        assertThat(clientSpan.annotations())
                .extracting(Annotation::value)
                .containsExactly("wire.send", "cache.miss");
        assertThat(clientSpan.tags()).contains(entry("env", "test"), entry("http.method", "GET"));
        assertThat(clientSpan.debug()).isTrue();

        Span updatedSpan = clientSpan.toBuilder()
                .id(PRODUCER_SPAN_ID)
                .name("publish")
                .clearAnnotations()
                .clearTags()
                .putTag("topic", "orders")
                .build();

        assertThat(updatedSpan.id()).isEqualTo(PRODUCER_SPAN_ID);
        assertThat(updatedSpan.name()).isEqualTo("publish");
        assertThat(updatedSpan.annotations()).isEmpty();
        assertThat(updatedSpan.tags()).containsOnly(entry("topic", "orders"));
        assertThat(clientSpan.id()).isEqualTo(CLIENT_SPAN_ID);
        assertThat(clientSpan.tags()).contains(entry("env", "test"), entry("http.method", "GET"));
    }

    @Test
    void encodesAndDecodesSpansAcrossSupportedFormats() {
        Endpoint frontend = endpoint("frontend", "192.168.0.1", 8080);
        Endpoint backend = endpoint("backend", "2001:db8::1", 9000);
        Span clientSpan = frontendClientSpan(frontend, backend);

        assertThat(SpanBytesDecoder.JSON_V2.decodeOne(SpanBytesEncoder.JSON_V2.encode(clientSpan)))
                .isEqualTo(clientSpan);
        assertThat(SpanBytesDecoder.THRIFT.decodeOne(SpanBytesEncoder.THRIFT.encode(clientSpan)))
                .isEqualTo(clientSpan);
        assertThat(SpanBytesDecoder.PROTO3.decodeOne(SpanBytesEncoder.PROTO3.encode(clientSpan)))
                .isEqualTo(clientSpan);

        byte[] jsonList = SpanBytesEncoder.JSON_V2.encodeList(List.of(clientSpan));
        assertThat(SpanBytesDecoder.JSON_V2.decodeList(jsonList)).containsExactly(clientSpan);
        assertThat(SpanBytesDecoderDetector.decoderForListMessage(jsonList)).isSameAs(SpanBytesDecoder.JSON_V2);

        byte[] thriftMessage = SpanBytesEncoder.THRIFT.encode(clientSpan);
        assertThat(SpanBytesDecoderDetector.decoderForMessage(thriftMessage)).isSameAs(SpanBytesDecoder.THRIFT);

        byte[] protoList = SpanBytesEncoder.PROTO3.encodeList(List.of(clientSpan));
        assertThat(SpanBytesDecoder.PROTO3.decodeList(ByteBuffer.wrap(protoList))).containsExactly(clientSpan);
        assertThat(SpanBytesDecoderDetector.decoderForListMessage(ByteBuffer.wrap(protoList)))
                .isSameAs(SpanBytesDecoder.PROTO3);
    }

    @Test
    void composesCallsAndRoundTripsDependencyLinks() throws IOException {
        assertThat(Span.normalizeTraceId(TRACE_ID)).isEqualTo(TRACE_ID);
        assertThat(Span.normalizeTraceId(PARENT_ID)).isEqualTo(PARENT_ID);
        assertThat(Call.create(List.of("frontend", "backend")).map(List::size).execute()).isEqualTo(2);
        assertThat(Call.create(List.of("frontend", "backend"))
                .flatMap(serviceNames -> Call.create(String.join(">", serviceNames)))
                .execute()).isEqualTo("frontend>backend");

        Call<String> failingCall = new Call<>() {
            @Override
            public String execute() throws IOException {
                throw new IOException("boom");
            }

            @Override
            public void enqueue(Callback<String> callback) {
                callback.onError(new IOException("boom"));
            }

            @Override
            public void cancel() {
            }

            @Override
            public boolean isCanceled() {
                return false;
            }

            @Override
            public Call<String> clone() {
                return this;
            }
        };

        assertThat(failingCall.handleError((throwable, callback) -> callback.onSuccess("fallback")).execute())
                .isEqualTo("fallback");

        DependencyLink dependencyLink = DependencyLink.newBuilder()
                .parent("frontend")
                .child("backend")
                .callCount(5)
                .errorCount(1)
                .build();
        DependencyLink updatedDependencyLink = dependencyLink.toBuilder().errorCount(2).build();

        assertThat(updatedDependencyLink.errorCount()).isEqualTo(2);
        assertThat(DependencyLinkBytesDecoder.JSON_V1.decodeOne(DependencyLinkBytesEncoder.JSON_V1.encode(dependencyLink)))
                .isEqualTo(dependencyLink);
        assertThat(DependencyLinkBytesDecoder.JSON_V1.decodeList(
                DependencyLinkBytesEncoder.JSON_V1.encodeList(List.of(dependencyLink))))
                .containsExactly(dependencyLink);
    }

    @Test
    void storesQueriesAndAggregatesSpansInMemory() throws IOException {
        Endpoint frontend = endpoint("frontend", "192.168.0.1", 8080);
        Endpoint backend = endpoint("backend", "192.168.0.2", 9000);
        Endpoint queue = endpoint("queue", "192.168.0.3", 5672);

        Span clientSpan = frontendClientSpan(frontend, backend);
        Span serverSpan = backendServerSpan(frontend, backend);
        Span producerSpan = producerSpan(frontend, queue);

        InMemoryStorage storage = InMemoryStorage.newBuilder()
                .autocompleteKeys(List.of("http.method", "env"))
                .maxSpanCount(10)
                .build();

        storage.accept(List.of(clientSpan, serverSpan, producerSpan)).execute();

        assertThat(storage.acceptedSpanCount()).isEqualTo(3);
        assertThat(storage.getServiceNames().execute()).containsExactly("backend", "frontend");
        assertThat(storage.getRemoteServiceNames("frontend").execute()).containsExactly("backend", "queue");
        assertThat(storage.getSpanNames("frontend").execute()).containsExactly("get", "publish");
        assertThat(storage.getKeys().execute()).containsExactly("http.method", "env");
        assertThat(storage.getValues("http.method").execute()).containsExactly("GET", "POST");
        assertThat(storage.getTrace(TRACE_ID).execute()).containsExactly(clientSpan, serverSpan);

        LinkedHashMap<String, String> annotationQuery = new LinkedHashMap<>();
        annotationQuery.put("cache.miss", "");
        annotationQuery.put("http.method", "GET");
        QueryRequest request = QueryRequest.newBuilder()
                .serviceName("frontend")
                .remoteServiceName("backend")
                .spanName("get")
                .annotationQuery(annotationQuery)
                .minDuration(400L)
                .endTs(20L)
                .lookback(20L)
                .limit(10)
                .build();

        assertThat(request.annotationQueryString()).isEqualTo("cache.miss and http.method=GET");
        assertThat(request.test(List.of(clientSpan, serverSpan))).isTrue();
        assertThat(request.test(List.of(producerSpan))).isFalse();
        assertThat(storage.getTraces(request).execute()).containsExactly(List.of(clientSpan, serverSpan));
        assertThat(storage.getDependencies(20L, 20L).execute()).containsExactly(
                DependencyLink.newBuilder().parent("frontend").child("backend").callCount(1).build());
    }

    @Test
    void evictsOldestStoredSpansWhenCapacityIsExceededAndClearResetsState() throws IOException {
        Endpoint frontend = endpoint("frontend", "192.168.0.1", 8080);
        Span oldestSpan = standaloneServerSpan("1", "1", 10_000L, frontend, "oldest");
        Span middleSpan = standaloneServerSpan("2", "2", 20_000L, frontend, "middle");
        Span newestSpan = standaloneServerSpan("3", "3", 30_000L, frontend, "newest");

        InMemoryStorage storage = InMemoryStorage.newBuilder()
                .maxSpanCount(2)
                .build();

        storage.accept(List.of(oldestSpan, middleSpan)).execute();
        storage.accept(List.of(newestSpan)).execute();

        assertThat(storage.acceptedSpanCount()).isEqualTo(3);
        assertThat(storage.getTrace(oldestSpan.traceId()).execute()).isEmpty();
        assertThat(storage.getTrace(middleSpan.traceId()).execute()).containsExactly(middleSpan);
        assertThat(storage.getTrace(newestSpan.traceId()).execute()).containsExactly(newestSpan);
        assertThat(storage.getTraces()).containsExactlyInAnyOrder(List.of(middleSpan), List.of(newestSpan));

        storage.clear();

        assertThat(storage.acceptedSpanCount()).isZero();
        assertThat(storage.getTrace(middleSpan.traceId()).execute()).isEmpty();
        assertThat(storage.getTraces()).isEmpty();
        assertThat(storage.getServiceNames().execute()).isEmpty();
    }

    @Test
    void separates64BitAnd128BitTraceIdsWhenStrictTraceIdIsEnabled() throws IOException {
        Endpoint frontend = endpoint("frontend", "192.168.0.1", 8080);
        Span longTraceSpan = Span.newBuilder()
                .traceId(TRACE_ID)
                .id(CLIENT_SPAN_ID)
                .kind(Span.Kind.SERVER)
                .name("GET")
                .timestamp(10_000L)
                .duration(100L)
                .localEndpoint(frontend)
                .build();
        Span shortTraceSpan = Span.newBuilder()
                .traceId(SHORT_TRACE_ID)
                .id(SHORT_TRACE_SPAN_ID)
                .kind(Span.Kind.SERVER)
                .name("GET")
                .timestamp(20_000L)
                .duration(100L)
                .localEndpoint(frontend)
                .build();
        QueryRequest request = QueryRequest.newBuilder()
                .serviceName("frontend")
                .endTs(30L)
                .lookback(30L)
                .limit(10)
                .build();

        InMemoryStorage nonStrictStorage = InMemoryStorage.newBuilder().strictTraceId(false).build();
        nonStrictStorage.accept(List.of(longTraceSpan, shortTraceSpan)).execute();

        assertThat(nonStrictStorage.getTrace(SHORT_TRACE_ID).execute()).containsExactly(longTraceSpan, shortTraceSpan);
        assertThat(nonStrictStorage.getTrace(TRACE_ID).execute()).containsExactly(longTraceSpan, shortTraceSpan);
        assertThat(nonStrictStorage.getTraces(request).execute()).containsExactly(List.of(longTraceSpan, shortTraceSpan));

        InMemoryStorage strictStorage = InMemoryStorage.newBuilder().strictTraceId(true).build();
        strictStorage.accept(List.of(longTraceSpan, shortTraceSpan)).execute();

        assertThat(strictStorage.getTrace(SHORT_TRACE_ID).execute()).containsExactly(shortTraceSpan);
        assertThat(strictStorage.getTrace(TRACE_ID).execute()).containsExactly(longTraceSpan);
        assertThat(strictStorage.getTraces(request).execute())
                .containsExactlyInAnyOrder(List.of(longTraceSpan), List.of(shortTraceSpan));
    }

    @Test
    void keepsDirectTraceLookupsAvailableWhenSearchIsDisabled() throws IOException {
        Endpoint frontend = endpoint("frontend", "192.168.0.1", 8080);
        Endpoint backend = endpoint("backend", "192.168.0.2", 9000);
        Span clientSpan = frontendClientSpan(frontend, backend);
        QueryRequest request = QueryRequest.newBuilder()
                .serviceName("frontend")
                .endTs(20L)
                .lookback(20L)
                .limit(10)
                .build();

        InMemoryStorage storage = InMemoryStorage.newBuilder()
                .searchEnabled(false)
                .autocompleteKeys(List.of("http.method", "env"))
                .build();

        storage.accept(List.of(clientSpan)).execute();

        assertThat(storage.getTrace(TRACE_ID).execute()).containsExactly(clientSpan);
        assertThat(storage.getServiceNames().execute()).isEmpty();
        assertThat(storage.getRemoteServiceNames("frontend").execute()).isEmpty();
        assertThat(storage.getSpanNames("frontend").execute()).isEmpty();
        assertThat(storage.getKeys().execute()).isEmpty();
        assertThat(storage.getValues("http.method").execute()).isEmpty();
        assertThat(storage.getTraces(request).execute()).isEmpty();
    }

    private static Endpoint endpoint(String serviceName, String ip, int port) {
        return Endpoint.newBuilder()
                .serviceName(serviceName)
                .ip(ip)
                .port(port)
                .build();
    }

    private static Span frontendClientSpan(Endpoint frontend, Endpoint backend) {
        return Span.newBuilder()
                .traceId(TRACE_ID)
                .parentId(PARENT_ID)
                .id(CLIENT_SPAN_ID)
                .kind(Span.Kind.CLIENT)
                .name("GET")
                .timestamp(10_000L)
                .duration(500L)
                .localEndpoint(frontend)
                .remoteEndpoint(backend)
                .addAnnotation(10_200L, "cache.miss")
                .addAnnotation(10_100L, "wire.send")
                .putTag("http.method", "GET")
                .putTag("env", "test")
                .debug(true)
                .build();
    }

    private static Span backendServerSpan(Endpoint frontend, Endpoint backend) {
        return Span.newBuilder()
                .traceId(TRACE_ID)
                .parentId(CLIENT_SPAN_ID)
                .id(SERVER_SPAN_ID)
                .kind(Span.Kind.SERVER)
                .name("GET")
                .timestamp(10_100L)
                .duration(300L)
                .localEndpoint(backend)
                .remoteEndpoint(frontend)
                .putTag("env", "test")
                .build();
    }

    private static Span producerSpan(Endpoint frontend, Endpoint queue) {
        return Span.newBuilder()
                .traceId(PRODUCER_TRACE_ID)
                .id(PRODUCER_SPAN_ID)
                .kind(Span.Kind.PRODUCER)
                .name("PUBLISH")
                .timestamp(40_000L)
                .duration(200L)
                .localEndpoint(frontend)
                .remoteEndpoint(queue)
                .putTag("http.method", "POST")
                .putTag("env", "test")
                .build();
    }

    private static Span standaloneServerSpan(String traceId, String spanId, long timestamp, Endpoint endpoint, String name) {
        return Span.newBuilder()
                .traceId(traceId)
                .id(spanId)
                .kind(Span.Kind.SERVER)
                .name(name)
                .timestamp(timestamp)
                .duration(100L)
                .localEndpoint(endpoint)
                .build();
    }
}
