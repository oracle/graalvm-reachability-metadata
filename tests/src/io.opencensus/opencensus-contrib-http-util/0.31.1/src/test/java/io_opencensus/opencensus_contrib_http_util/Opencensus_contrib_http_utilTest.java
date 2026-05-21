/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opencensus.opencensus_contrib_http_util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opencensus.contrib.http.HttpClientHandler;
import io.opencensus.contrib.http.HttpExtractor;
import io.opencensus.contrib.http.HttpRequestContext;
import io.opencensus.contrib.http.HttpServerHandler;
import io.opencensus.contrib.http.util.HttpMeasureConstants;
import io.opencensus.contrib.http.util.HttpPropagationUtil;
import io.opencensus.contrib.http.util.HttpTraceAttributeConstants;
import io.opencensus.contrib.http.util.HttpTraceUtil;
import io.opencensus.contrib.http.util.HttpViewConstants;
import io.opencensus.contrib.http.util.HttpViews;
import io.opencensus.stats.View;
import io.opencensus.tags.TagKey;
import io.opencensus.trace.Annotation;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.EndSpanOptions;
import io.opencensus.trace.Link;
import io.opencensus.trace.MessageEvent;
import io.opencensus.trace.Sampler;
import io.opencensus.trace.Span;
import io.opencensus.trace.SpanBuilder;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.SpanId;
import io.opencensus.trace.Status;
import io.opencensus.trace.TraceId;
import io.opencensus.trace.TraceOptions;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.propagation.SpanContextParseException;
import io.opencensus.trace.propagation.TextFormat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Opencensus_contrib_http_utilTest {
    private static final String TRACE_ID = "0102030405060708090a0b0c0d0e0f10";
    private static final String SPAN_ID = "0000000000003039";
    private static final String TRACE_HEADER = TRACE_ID + "/12345;o=1";
    private static final SpanContext SAMPLED_CONTEXT = spanContext(TRACE_ID, SPAN_ID, true);
    private static final TextFormat CLOUD_TRACE_FORMAT = HttpPropagationUtil.getCloudTraceFormat();
    private static final TextFormat.Setter<Map<String, String>> MAP_SETTER =
            new TextFormat.Setter<Map<String, String>>() {
                @Override
                public void put(Map<String, String> carrier, String key, String value) {
                    carrier.put(key, value);
                }
            };
    private static final TextFormat.Getter<Map<String, String>> MAP_GETTER =
            new TextFormat.Getter<Map<String, String>>() {
                @Override
                public String get(Map<String, String> carrier, String key) {
                    return carrier.get(key);
                }
            };

    @Test
    void cloudTraceFormatInjectsAndExtractsGoogleTraceHeader() throws Exception {
        Map<String, String> carrier = new HashMap<>();

        CLOUD_TRACE_FORMAT.inject(SAMPLED_CONTEXT, carrier, MAP_SETTER);
        SpanContext extracted = CLOUD_TRACE_FORMAT.extract(carrier, MAP_GETTER);

        assertThat(CLOUD_TRACE_FORMAT.fields()).containsExactly("X-Cloud-Trace-Context");
        assertThat(carrier).containsEntry("X-Cloud-Trace-Context", TRACE_HEADER);
        assertThat(extracted.getTraceId()).isEqualTo(SAMPLED_CONTEXT.getTraceId());
        assertThat(extracted.getSpanId()).isEqualTo(SAMPLED_CONTEXT.getSpanId());
        assertThat(extracted.getTraceOptions().isSampled()).isTrue();
    }

    @Test
    void cloudTraceFormatExtractsUnsampledHeaderAndRejectsInvalidInput() throws Exception {
        Map<String, String> carrier = new HashMap<>();
        carrier.put("X-Cloud-Trace-Context", TRACE_ID + "/12345");

        SpanContext extracted = CLOUD_TRACE_FORMAT.extract(carrier, MAP_GETTER);

        assertThat(extracted.getTraceId().toLowerBase16()).isEqualTo(TRACE_ID);
        assertThat(extracted.getSpanId().toLowerBase16()).isEqualTo(SPAN_ID);
        assertThat(extracted.getTraceOptions().isSampled()).isFalse();

        carrier.put("X-Cloud-Trace-Context", "not-a-valid-cloud-trace-header");
        assertThatThrownBy(() -> CLOUD_TRACE_FORMAT.extract(carrier, MAP_GETTER))
                .isInstanceOf(SpanContextParseException.class);
    }

    @Test
    void httpTraceUtilMapsResponseStatusCodesAndErrorDescriptions() {
        assertStatus(HttpTraceUtil.parseResponseStatus(204, null), Status.CanonicalCode.OK, null);
        assertStatus(HttpTraceUtil.parseResponseStatus(400, new IllegalArgumentException("bad input")),
                Status.CanonicalCode.INVALID_ARGUMENT, "bad input");
        assertStatus(HttpTraceUtil.parseResponseStatus(401, null), Status.CanonicalCode.UNAUTHENTICATED, null);
        assertStatus(HttpTraceUtil.parseResponseStatus(403, null), Status.CanonicalCode.PERMISSION_DENIED, null);
        assertStatus(HttpTraceUtil.parseResponseStatus(404, new IllegalStateException()),
                Status.CanonicalCode.NOT_FOUND, "IllegalStateException");
        assertStatus(HttpTraceUtil.parseResponseStatus(429, null), Status.CanonicalCode.RESOURCE_EXHAUSTED, null);
        assertStatus(HttpTraceUtil.parseResponseStatus(501, null), Status.CanonicalCode.UNIMPLEMENTED, null);
        assertStatus(HttpTraceUtil.parseResponseStatus(503, null), Status.CanonicalCode.UNAVAILABLE, null);
        assertStatus(HttpTraceUtil.parseResponseStatus(504, null), Status.CanonicalCode.DEADLINE_EXCEEDED, null);
        assertStatus(HttpTraceUtil.parseResponseStatus(0, new RuntimeException("transport failed")),
                Status.CanonicalCode.UNKNOWN, "transport failed");
        assertStatus(HttpTraceUtil.parseResponseStatus(418, null), Status.CanonicalCode.UNKNOWN, null);
    }

    @Test
    void httpViewsAndConstantsExposeClientAndServerMetricDefinitions() {
        assertThat(HttpMeasureConstants.HTTP_CLIENT_METHOD.getName()).isEqualTo("http_client_method");
        assertThat(HttpMeasureConstants.HTTP_SERVER_ROUTE.getName()).isEqualTo("http_server_route");
        assertThat(HttpMeasureConstants.HTTP_CLIENT_ROUNDTRIP_LATENCY.getUnit()).isEqualTo("ms");
        assertThat(HttpMeasureConstants.HTTP_SERVER_RECEIVED_BYTES.getUnit()).isEqualTo("By");
        assertThat(HttpTraceAttributeConstants.HTTP_STATUS_CODE).isEqualTo("http.status_code");

        assertView(HttpViewConstants.HTTP_CLIENT_COMPLETED_COUNT_VIEW,
                "opencensus.io/http/client/completed_count",
                HttpMeasureConstants.HTTP_CLIENT_ROUNDTRIP_LATENCY.getName(),
                HttpMeasureConstants.HTTP_CLIENT_METHOD,
                HttpMeasureConstants.HTTP_CLIENT_STATUS);
        assertView(HttpViewConstants.HTTP_CLIENT_SENT_BYTES_VIEW,
                "opencensus.io/http/client/sent_bytes",
                HttpMeasureConstants.HTTP_CLIENT_SENT_BYTES.getName(),
                HttpMeasureConstants.HTTP_CLIENT_METHOD,
                HttpMeasureConstants.HTTP_CLIENT_STATUS);
        assertView(HttpViewConstants.HTTP_SERVER_LATENCY_VIEW,
                "opencensus.io/http/server/server_latency",
                HttpMeasureConstants.HTTP_SERVER_LATENCY.getName(),
                HttpMeasureConstants.HTTP_SERVER_METHOD,
                HttpMeasureConstants.HTTP_SERVER_ROUTE,
                HttpMeasureConstants.HTTP_SERVER_STATUS);

        HttpViews.registerAllClientViews();
        HttpViews.registerAllServerViews();
        HttpViews.registerAllViews();
    }

    @Test
    void clientHandlerStartsSpanPropagatesContextRecordsMessagesAndEndsSpan() {
        RecordingTracer tracer = new RecordingTracer(SAMPLED_CONTEXT);
        HttpClientHandler<TestRequest, TestResponse, Map<String, String>> handler = new HttpClientHandler<>(
                tracer, new TestExtractor(), CLOUD_TRACE_FORMAT, MAP_SETTER);
        Map<String, String> carrier = new HashMap<>();
        TestRequest request = new TestRequest("GET", "example.com", "items/42", "/items/{id}",
                "https://example.com/items/42", "JUnit");

        HttpRequestContext context = handler.handleStart(null, carrier, request);
        handler.handleMessageSent(context, 13L);
        handler.handleMessageReceived(context, 21L);
        handler.handleEnd(context, request, new TestResponse(404), new IllegalStateException("missing"));

        RecordingSpan span = tracer.startedSpans.get(0);
        RecordingSpanBuilder builder = tracer.startedBuilders.get(0);
        assertThat(builder.name).isEqualTo("/items/42");
        assertThat(builder.kind).isEqualTo(Span.Kind.CLIENT);
        assertThat(builder.explicitParent).isNotNull();
        assertThat(carrier).containsEntry("X-Cloud-Trace-Context", TRACE_HEADER);
        assertThat(handler.getSpanFromContext(context)).isSameAs(span);
        assertThat(span.attributes.keySet()).contains(
                HttpTraceAttributeConstants.HTTP_USER_AGENT,
                HttpTraceAttributeConstants.HTTP_HOST,
                HttpTraceAttributeConstants.HTTP_METHOD,
                HttpTraceAttributeConstants.HTTP_PATH,
                HttpTraceAttributeConstants.HTTP_ROUTE,
                HttpTraceAttributeConstants.HTTP_URL,
                HttpTraceAttributeConstants.HTTP_STATUS_CODE);
        assertThat(span.messageEvents).extracting(MessageEvent::getType)
                .containsExactly(MessageEvent.Type.SENT, MessageEvent.Type.RECEIVED);
        assertThat(span.messageEvents).extracting(MessageEvent::getUncompressedMessageSize)
                .containsExactly(13L, 21L);
        assertStatus(span.status, Status.CanonicalCode.NOT_FOUND, "missing");
        assertThat(span.ended).isTrue();
    }

    @Test
    void serverHandlerUsesRemoteParentWhenHeaderIsPresent() {
        Map<String, String> carrier = new HashMap<>();
        carrier.put("X-Cloud-Trace-Context", TRACE_HEADER);
        RecordingTracer tracer = new RecordingTracer(
                spanContext("11111111111111111111111111111111", "0000000000000002", true));
        HttpServerHandler<TestRequest, TestResponse, Map<String, String>> handler = new HttpServerHandler<>(
                tracer, new TestExtractor(), CLOUD_TRACE_FORMAT, MAP_GETTER, false);
        TestRequest request = new TestRequest("POST", "api.example.com", "/submit", "/submit",
                "https://api.example.com/submit", "integration-test");

        HttpRequestContext context = handler.handleStart(carrier, request);
        handler.handleMessageReceived(context, 34L);
        handler.handleMessageSent(context, 55L);
        handler.handleEnd(context, request, new TestResponse(503), null);

        RecordingSpan span = tracer.startedSpans.get(0);
        RecordingSpanBuilder builder = tracer.startedBuilders.get(0);
        assertThat(builder.name).isEqualTo("/submit");
        assertThat(builder.kind).isEqualTo(Span.Kind.SERVER);
        assertThat(builder.remoteParent).isEqualTo(SAMPLED_CONTEXT);
        assertThat(handler.getSpanFromContext(context)).isSameAs(span);
        assertThat(span.messageEvents).extracting(MessageEvent::getType)
                .containsExactly(MessageEvent.Type.RECEIVED, MessageEvent.Type.SENT);
        assertStatus(span.status, Status.CanonicalCode.UNAVAILABLE, null);
        assertThat(span.ended).isTrue();
    }

    @Test
    void publicEndpointServerLinksRemoteContextInsteadOfUsingItAsParent() {
        Map<String, String> carrier = new HashMap<>();
        carrier.put("X-Cloud-Trace-Context", TRACE_HEADER);
        RecordingTracer tracer = new RecordingTracer(
                spanContext("22222222222222222222222222222222", "0000000000000003", true));
        HttpServerHandler<TestRequest, TestResponse, Map<String, String>> handler = new HttpServerHandler<>(
                tracer, new TestExtractor(), CLOUD_TRACE_FORMAT, MAP_GETTER, true);
        TestRequest request = new TestRequest("GET", "public.example.com", null, null,
                "https://public.example.com", null);

        HttpRequestContext context = handler.handleStart(carrier, request);
        handler.handleEnd(context, request, new TestResponse(200), null);

        RecordingSpan span = tracer.startedSpans.get(0);
        RecordingSpanBuilder builder = tracer.startedBuilders.get(0);
        assertThat(builder.name).isEqualTo("/");
        assertThat(builder.remoteParent).isNull();
        assertThat(span.links).hasSize(1);
        Link link = span.links.get(0);
        assertThat(link.getTraceId()).isEqualTo(SAMPLED_CONTEXT.getTraceId());
        assertThat(link.getSpanId()).isEqualTo(SAMPLED_CONTEXT.getSpanId());
        assertThat(link.getType()).isEqualTo(Link.Type.PARENT_LINKED_SPAN);
        assertStatus(span.status, Status.CanonicalCode.OK, null);
        assertThat(span.ended).isTrue();
    }

    @Test
    void serverHandlerIgnoresMalformedTraceHeaderAndStartsNewServerSpan() {
        Map<String, String> carrier = new HashMap<>();
        carrier.put("X-Cloud-Trace-Context", "not-a-valid-cloud-trace-header");
        RecordingTracer tracer = new RecordingTracer(
                spanContext("33333333333333333333333333333333", "0000000000000004", true));
        HttpServerHandler<TestRequest, TestResponse, Map<String, String>> handler = new HttpServerHandler<>(
                tracer, new TestExtractor(), CLOUD_TRACE_FORMAT, MAP_GETTER, false);
        TestRequest request = new TestRequest("GET", "api.example.com", "/health", null,
                "https://api.example.com/health", "health-check");

        HttpRequestContext context = handler.handleStart(carrier, request);
        handler.handleEnd(context, request, new TestResponse(200), null);

        RecordingSpan span = tracer.startedSpans.get(0);
        RecordingSpanBuilder builder = tracer.startedBuilders.get(0);
        assertThat(builder.name).isEqualTo("/health");
        assertThat(builder.kind).isEqualTo(Span.Kind.SERVER);
        assertThat(builder.remoteParent).isNull();
        assertThat(span.links).isEmpty();
        assertThat(handler.getSpanFromContext(context)).isSameAs(span);
        assertStatus(span.status, Status.CanonicalCode.OK, null);
        assertThat(span.ended).isTrue();
    }

    private static void assertStatus(Status status, Status.CanonicalCode canonicalCode, String description) {
        assertThat(status.getCanonicalCode()).isEqualTo(canonicalCode);
        assertThat(status.getDescription()).isEqualTo(description);
    }

    private static void assertView(View view, String name, String measureName, TagKey... columns) {
        assertThat(view.getName().asString()).isEqualTo(name);
        assertThat(view.getMeasure().getName()).isEqualTo(measureName);
        assertThat(view.getColumns()).containsExactly(columns);
    }

    private static SpanContext spanContext(String traceId, String spanId, boolean sampled) {
        return SpanContext.create(
                TraceId.fromLowerBase16(traceId),
                SpanId.fromLowerBase16(spanId),
                TraceOptions.builder().setIsSampled(sampled).build());
    }

    private static final class TestRequest {
        private final String method;
        private final String host;
        private final String path;
        private final String route;
        private final String url;
        private final String userAgent;

        private TestRequest(String method, String host, String path, String route, String url, String userAgent) {
            this.method = method;
            this.host = host;
            this.path = path;
            this.route = route;
            this.url = url;
            this.userAgent = userAgent;
        }
    }

    private static final class TestResponse {
        private final int statusCode;

        private TestResponse(int statusCode) {
            this.statusCode = statusCode;
        }
    }

    private static final class TestExtractor extends HttpExtractor<TestRequest, TestResponse> {
        @Override
        public String getRoute(TestRequest request) {
            return request.route;
        }

        @Override
        public String getUrl(TestRequest request) {
            return request.url;
        }

        @Override
        public String getHost(TestRequest request) {
            return request.host;
        }

        @Override
        public String getMethod(TestRequest request) {
            return request.method;
        }

        @Override
        public String getPath(TestRequest request) {
            return request.path;
        }

        @Override
        public String getUserAgent(TestRequest request) {
            return request.userAgent;
        }

        @Override
        public int getStatusCode(TestResponse response) {
            return response.statusCode;
        }
    }

    private static final class RecordingTracer extends Tracer {
        private final List<RecordingSpanBuilder> startedBuilders = new ArrayList<>();
        private final List<RecordingSpan> startedSpans = new ArrayList<>();
        private final SpanContext nextSpanContext;

        private RecordingTracer(SpanContext nextSpanContext) {
            this.nextSpanContext = nextSpanContext;
        }

        @Override
        public SpanBuilder spanBuilderWithExplicitParent(String spanName, Span parent) {
            return newBuilder(spanName, parent, null);
        }

        @Override
        public SpanBuilder spanBuilderWithRemoteParent(String spanName, SpanContext remoteParent) {
            return newBuilder(spanName, null, remoteParent);
        }

        private RecordingSpanBuilder newBuilder(String name, Span explicitParent, SpanContext remoteParent) {
            RecordingSpanBuilder builder = new RecordingSpanBuilder(
                    this, name, explicitParent, remoteParent, nextSpanContext);
            startedBuilders.add(builder);
            return builder;
        }
    }

    private static final class RecordingSpanBuilder extends SpanBuilder {
        private final RecordingTracer tracer;
        private final String name;
        private final Span explicitParent;
        private final SpanContext remoteParent;
        private final SpanContext spanContext;
        private Span.Kind kind;
        private boolean recordEvents = true;

        private RecordingSpanBuilder(
                RecordingTracer tracer, String name, Span explicitParent, SpanContext remoteParent,
                SpanContext spanContext) {
            this.tracer = tracer;
            this.name = name;
            this.explicitParent = explicitParent;
            this.remoteParent = remoteParent;
            this.spanContext = spanContext;
        }

        @Override
        public SpanBuilder setSampler(Sampler sampler) {
            return this;
        }

        @Override
        public SpanBuilder setParentLinks(List<Span> parentLinks) {
            return this;
        }

        @Override
        public SpanBuilder setRecordEvents(boolean recordEvents) {
            this.recordEvents = recordEvents;
            return this;
        }

        @Override
        public SpanBuilder setSpanKind(Span.Kind kind) {
            this.kind = kind;
            return this;
        }

        @Override
        public Span startSpan() {
            RecordingSpan span = new RecordingSpan(spanContext, recordEvents);
            tracer.startedSpans.add(span);
            return span;
        }
    }

    private static final class RecordingSpan extends Span {
        private final Map<String, AttributeValue> attributes = new HashMap<>();
        private final List<MessageEvent> messageEvents = new ArrayList<>();
        private final List<Link> links = new ArrayList<>();
        private Status status;
        private boolean ended;

        private RecordingSpan(SpanContext context, boolean recordEvents) {
            super(context, recordEvents ? EnumSet.of(Span.Options.RECORD_EVENTS) : EnumSet.noneOf(Span.Options.class));
        }

        @Override
        public void putAttribute(String key, AttributeValue value) {
            attributes.put(key, value);
        }

        @Override
        public void putAttributes(Map<String, AttributeValue> attributes) {
            this.attributes.putAll(attributes);
        }

        @Override
        public void addAnnotation(String description, Map<String, AttributeValue> attributes) {
        }

        @Override
        public void addAnnotation(Annotation annotation) {
        }

        @Override
        public void addMessageEvent(MessageEvent messageEvent) {
            messageEvents.add(messageEvent);
        }

        @Override
        public void addLink(Link link) {
            links.add(link);
        }

        @Override
        public void setStatus(Status status) {
            this.status = status;
        }

        @Override
        public void end(EndSpanOptions options) {
            ended = true;
        }
    }
}
