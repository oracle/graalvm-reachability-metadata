/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_zipkin_zipkin2.zipkin;

import org.junit.jupiter.api.Test;
import zipkin2.Annotation;
import zipkin2.DependencyLink;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.SpanBytesDecoderDetector;
import zipkin2.codec.BytesDecoder;
import zipkin2.codec.DependencyLinkBytesDecoder;
import zipkin2.codec.DependencyLinkBytesEncoder;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.storage.InMemoryStorage;
import zipkin2.storage.QueryRequest;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipkinTest {
    private static final String TRACE_ID = "463ac35c9f6413ad48485a3953bb6124";
    private static final String ROOT_SPAN_ID = "a2fb4a1d1a96d312";
    private static final String CHILD_SPAN_ID = "b2fb4a1d1a96d313";

    @Test
    void spanBuilderNormalizesFieldsAndSupportsToBuilder() {
        Endpoint localEndpoint = Endpoint.newBuilder()
                .serviceName("FrontEnd")
                .ip("192.168.1.10")
                .port(8080)
                .build();
        Endpoint remoteEndpoint = Endpoint.newBuilder()
                .serviceName("BackEnd")
                .ip("2001:db8::1")
                .port(9000)
                .build();

        Span span = Span.newBuilder()
                .traceId(0L, 0x463ac35c9f6413adL)
                .parentId("463ac35c9f6413ad")
                .id(ROOT_SPAN_ID)
                .kind(Span.Kind.CLIENT)
                .name("Get /Api")
                .timestamp(1_000L)
                .duration(350L)
                .localEndpoint(localEndpoint)
                .remoteEndpoint(remoteEndpoint)
                .addAnnotation(1_200L, "cache.miss")
                .addAnnotation(1_100L, "wire.send")
                .putTag("http.method", "GET")
                .putTag("http.path", "/orders")
                .debug(true)
                .shared(false)
                .build();

        Span updated = span.toBuilder()
                .putTag("env", "prod")
                .build();

        assertThat(localEndpoint.serviceName()).isEqualTo("frontend");
        assertThat(localEndpoint.ipv4()).isEqualTo("192.168.1.10");
        assertThat(localEndpoint.portAsInt()).isEqualTo(8080);
        assertThat(remoteEndpoint.serviceName()).isEqualTo("backend");
        assertThat(remoteEndpoint.ipv6()).isEqualTo("2001:db8::1");
        assertThat(remoteEndpoint.portAsInt()).isEqualTo(9000);

        assertThat(span.traceId()).isEqualTo("463ac35c9f6413ad");
        assertThat(span.parentId()).isEqualTo("463ac35c9f6413ad");
        assertThat(span.id()).isEqualTo(ROOT_SPAN_ID);
        assertThat(span.name()).isEqualTo("get /api");
        assertThat(span.localServiceName()).isEqualTo("frontend");
        assertThat(span.remoteServiceName()).isEqualTo("backend");
        assertThat(span.timestampAsLong()).isEqualTo(1_000L);
        assertThat(span.durationAsLong()).isEqualTo(350L);
        assertThat(span.debug()).isTrue();
        assertThat(span.shared()).isFalse();
        assertThat(span.annotations())
                .containsExactly(
                        Annotation.create(1_100L, "wire.send"),
                        Annotation.create(1_200L, "cache.miss")
                );
        assertThat(span.tags()).containsExactly(
                Map.entry("http.method", "GET"),
                Map.entry("http.path", "/orders")
        );

        assertThat(updated.tags()).containsExactly(
                Map.entry("env", "prod"),
                Map.entry("http.method", "GET"),
                Map.entry("http.path", "/orders")
        );
        assertThat(span.tags()).containsExactly(
                Map.entry("http.method", "GET"),
                Map.entry("http.path", "/orders")
        );
    }

    @Test
    void spanCodecsRoundTripAcrossAllEncodings() {
        Span span = newClientSpan();

        for (SpanBytesEncoder encoder : SpanBytesEncoder.values()) {
            SpanBytesDecoder decoder = SpanBytesDecoder.valueOf(encoder.name());
            byte[] encodedSpan = encoder.encode(span);
            byte[] encodedSpanList = encoder.encodeList(List.of(span));
            List<Span> decodedIntoCollection = new ArrayList<>();

            boolean decodedList = decoder.decodeList(encodedSpanList, decodedIntoCollection);

            assertThat(decoder.decodeOne(encodedSpan)).isEqualTo(span);
            assertThat(decoder.decodeList(encodedSpanList)).containsExactly(span);
            assertThat(decodedList).isTrue();
            assertThat(decodedIntoCollection).containsExactly(span);
            assertThat(decoder.encoding()).isEqualTo(encoder.encoding());
        }
    }

    @Test
    void spanBytesDecoderDetectorDetectsListFormatsAndRejectsUnsupportedSingleMessageDetection() {
        Span span = newClientSpan();

        for (SpanBytesEncoder encoder : SpanBytesEncoder.values()) {
            SpanBytesDecoder expectedDecoder = SpanBytesDecoder.valueOf(encoder.name());
            byte[] encodedSpanList = encoder.encodeList(List.of(span));
            BytesDecoder<Span> detectedFromBytes = SpanBytesDecoderDetector.decoderForListMessage(encodedSpanList);
            BytesDecoder<Span> detectedFromBuffer = SpanBytesDecoderDetector.decoderForListMessage(ByteBuffer.wrap(encodedSpanList));

            assertThat(detectedFromBytes).isSameAs(expectedDecoder);
            assertThat(detectedFromBuffer).isSameAs(expectedDecoder);
            assertThat(detectedFromBytes.decodeList(encodedSpanList)).containsExactly(span);
            assertThat(detectedFromBuffer.decodeList(encodedSpanList)).containsExactly(span);
        }

        for (SpanBytesEncoder encoder : List.of(SpanBytesEncoder.JSON_V1, SpanBytesEncoder.THRIFT)) {
            byte[] encodedSpan = encoder.encode(span);
            BytesDecoder<Span> detectedDecoder = SpanBytesDecoderDetector.decoderForMessage(encodedSpan);

            assertThat(detectedDecoder).isSameAs(SpanBytesDecoder.valueOf(encoder.name()));
            assertThat(detectedDecoder.decodeOne(encodedSpan)).isEqualTo(span);
        }

        for (SpanBytesEncoder encoder : List.of(SpanBytesEncoder.JSON_V2, SpanBytesEncoder.PROTO3)) {
            byte[] encodedSpan = encoder.encode(span);

            assertThatThrownBy(() -> SpanBytesDecoderDetector.decoderForMessage(encodedSpan))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void inMemoryStorageIndexesQueriesAndEncodesDependencies() throws Exception {
        Span clientSpan = newClientSpan().toBuilder().putTag("env", "prod").build();
        Span serverSpan = Span.newBuilder()
                .traceId(TRACE_ID)
                .parentId(ROOT_SPAN_ID)
                .id(CHILD_SPAN_ID)
                .kind(Span.Kind.SERVER)
                .name("Get /Api")
                .timestamp(1_500L)
                .duration(500L)
                .localEndpoint(Endpoint.newBuilder().serviceName("BackEnd").ip("2001:db8::1").port(9000).build())
                .putTag("http.method", "GET")
                .putTag("error", "timeout")
                .build();

        InMemoryStorage storage = InMemoryStorage.newBuilder()
                .autocompleteKeys(List.of("http.method", "env"))
                .build();

        storage.accept(List.of(clientSpan, serverSpan)).execute();

        QueryRequest request = QueryRequest.newBuilder()
                .serviceName("frontend")
                .spanName("get /api")
                .parseAnnotationQuery("http.method=GET and cache.miss")
                .endTs(10_000L)
                .lookback(10_000L)
                .limit(10)
                .build();

        List<List<Span>> traces = storage.getTraces(request).execute();
        List<DependencyLink> dependencyLinks = storage.getDependencies(10_000L, 10_000L).execute();
        DependencyLink dependencyLink = dependencyLinks.get(0);
        byte[] encodedDependencyLinks = DependencyLinkBytesEncoder.JSON_V1.encodeList(dependencyLinks);
        List<DependencyLink> decodedDependencyLinks = DependencyLinkBytesDecoder.JSON_V1.decodeList(encodedDependencyLinks);

        assertThat(storage.acceptedSpanCount()).isEqualTo(2);
        assertThat(storage.getServiceNames().execute()).containsExactly("backend", "frontend");
        assertThat(storage.getRemoteServiceNames("frontend").execute()).containsExactly("backend");
        assertThat(storage.getSpanNames("frontend").execute()).containsExactly("get /api");
        assertThat(storage.getKeys().execute()).containsExactly("http.method", "env");
        assertThat(storage.getValues("http.method").execute()).containsExactly("GET");
        assertThat(storage.getValues("env").execute()).containsExactly("prod");
        assertThat(storage.getTrace(TRACE_ID).execute()).containsExactly(clientSpan, serverSpan);

        assertThat(request.annotationQuery()).containsExactly(
                Map.entry("http.method", "GET"),
                Map.entry("cache.miss", "")
        );
        assertThat(request.annotationQueryString()).isEqualTo("http.method=GET and cache.miss");
        assertThat(request.test(List.of(clientSpan, serverSpan))).isTrue();
        assertThat(traces).singleElement().satisfies(trace -> assertThat(trace).containsExactly(clientSpan, serverSpan));

        assertThat(dependencyLinks).singleElement().isEqualTo(
                DependencyLink.newBuilder()
                        .parent("frontend")
                        .child("backend")
                        .callCount(1L)
                        .errorCount(1L)
                        .build()
        );
        assertThat(dependencyLink.toBuilder().build()).isEqualTo(dependencyLink);
        assertThat(decodedDependencyLinks).containsExactly(dependencyLink);
    }

    private static Span newClientSpan() {
        return Span.newBuilder()
                .traceId(TRACE_ID)
                .id(ROOT_SPAN_ID)
                .kind(Span.Kind.CLIENT)
                .name("Get /Api")
                .timestamp(1_000L)
                .duration(350L)
                .localEndpoint(Endpoint.newBuilder().serviceName("FrontEnd").ip("192.168.1.10").port(8080).build())
                .remoteEndpoint(Endpoint.newBuilder().serviceName("BackEnd").ip("2001:db8::1").port(9000).build())
                .addAnnotation(1_200L, "cache.miss")
                .addAnnotation(1_100L, "wire.send")
                .putTag("http.method", "GET")
                .putTag("http.path", "/orders")
                .debug(true)
                .build();
    }
}
