/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises cross-service context propagation: `OpenTelemetrySdk` instances built with composite
 * `ContextPropagators` that inject and extract W3C trace context, W3C baggage, B3 and Jaeger
 * headers so that one trace and its baggage survive hops between services.
 *
 * A mobile client calls the storefront with a W3C `traceparent`, a vendor `tracestate` and a
 * `baggage` header. The storefront continues that trace, adds the customer tier to the baggage
 * and calls the inventory service, writing every header format it speaks. A legacy proxy in front
 * of the inventory service forwards only the Jaeger `uber-trace-id` and `uberctx-` headers, and the
 * inventory service, midway through a move from Jaeger to B3, writes Jaeger, a single `b3` header
 * and W3C baggage. The pricing ingress forwards only `b3` and `baggage`. Each service is its own
 * SDK with its own exporter, and the spans they export form one parent chain under the client's
 * trace.
 *
 * A second request arrives unsampled, and every service honours that decision without
 * exporting anything while still forwarding the trace identity.
 */
class StorefrontPropagationTest {

    private static final String CLIENT_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private static final String CLIENT_SPAN_ID = "00f067aa0ba902b7";

    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");

    private static final AttributeKey<String> CUSTOMER_TIER = AttributeKey.stringKey("customer.tier");

    private static final AttributeKey<String> SESSION_ID = AttributeKey.stringKey("session.id");

    private static final TextMapSetter<Map<String, String>> HEADER_SETTER = Map::put;

    private static final TextMapGetter<Map<String, String>> HEADER_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    private final List<Service> services = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Service service : services) {
            assertThat(service.sdk.getSdkTracerProvider().shutdown().join(5, TimeUnit.SECONDS).isSuccess())
                    .isTrue();
        }
    }

    @Test
    void traceAndBaggageSurviveEveryHeaderFormat() {
        Service storefront = service("storefront", TextMapPropagator.composite(
                W3CTraceContextPropagator.getInstance(),
                W3CBaggagePropagator.getInstance(),
                B3Propagator.injectingMultiHeaders(),
                JaegerPropagator.getInstance()));
        Service inventory = service("inventory", TextMapPropagator.composite(
                JaegerPropagator.getInstance(),
                B3Propagator.injectingSingleHeader(),
                W3CBaggagePropagator.getInstance()));
        Service pricing = service("pricing", TextMapPropagator.composite(
                W3CTraceContextPropagator.getInstance(),
                B3Propagator.injectingSingleHeader(),
                W3CBaggagePropagator.getInstance()));

        Map<String, String> clientRequest = new LinkedHashMap<>();
        clientRequest.put("traceparent", "00-" + CLIENT_TRACE_ID + "-" + CLIENT_SPAN_ID + "-01");
        clientRequest.put("tracestate", "mobile=ios");
        clientRequest.put("baggage", "session.id=s-77");

        Map<String, String> inventoryRequest = storefront.handle(clientRequest, "GET /cart",
                () -> Baggage.current().toBuilder().put("customer.tier", "gold").build());

        String storefrontSpanId = storefront.exportedClientSpan().getSpanId();
        assertThat(inventoryRequest)
                .containsEntry("traceparent", "00-" + CLIENT_TRACE_ID + "-" + storefrontSpanId + "-01")
                .containsEntry("tracestate", "mobile=ios")
                .containsEntry("X-B3-TraceId", CLIENT_TRACE_ID)
                .containsEntry("X-B3-SpanId", storefrontSpanId)
                .containsEntry("X-B3-Sampled", "1")
                .containsEntry("uberctx-customer.tier", "gold")
                .containsEntry("uberctx-session.id", "s-77");
        assertThat(inventoryRequest.get("uber-trace-id"))
                .startsWith(CLIENT_TRACE_ID + ":" + storefrontSpanId + ":")
                .endsWith(":1");
        assertThat(inventoryRequest.get("baggage"))
                .contains("customer.tier=gold")
                .contains("session.id=s-77");

        Map<String, String> proxied = forwardedHeaders(inventoryRequest, "uber-trace-id", "uberctx-");
        Map<String, String> inventoryCall = inventory.handle(proxied, "reserve stock", Baggage::current);

        String inventorySpanId = inventory.exportedClientSpan().getSpanId();
        assertThat(inventoryCall)
                .containsEntry("b3", CLIENT_TRACE_ID + "-" + inventorySpanId + "-1")
                .containsEntry("uberctx-customer.tier", "gold")
                .doesNotContainKey("traceparent")
                .doesNotContainKey("X-B3-TraceId");
        assertThat(inventoryCall.get("uber-trace-id")).startsWith(CLIENT_TRACE_ID + ":" + inventorySpanId + ":");
        Map<String, String> pricingRequest = forwardedHeaders(inventoryCall, "b3", "baggage");
        assertThat(pricingRequest).containsOnlyKeys("b3", "baggage");
        assertThat(pricingRequest.get("baggage"))
                .contains("customer.tier=gold")
                .contains("session.id=s-77");

        Map<String, String> pricingResponse = pricing.handle(pricingRequest, "quote", Baggage::current);
        assertThat(pricingResponse).containsEntry("traceparent",
                "00-" + CLIENT_TRACE_ID + "-" + pricing.exportedClientSpan().getSpanId() + "-01");

        SpanData storefrontServer = storefront.exportedServerSpan();
        SpanData inventoryServer = inventory.exportedServerSpan();
        SpanData pricingServer = pricing.exportedServerSpan();

        assertThat(storefrontServer.getParentSpanId()).isEqualTo(CLIENT_SPAN_ID);
        assertThat(storefrontServer.getParentSpanContext().isRemote()).isTrue();
        assertThat(storefrontServer.getSpanContext().getTraceState().get("mobile")).isEqualTo("ios");
        assertThat(inventoryServer.getParentSpanId()).isEqualTo(storefrontSpanId);
        assertThat(inventoryServer.getParentSpanContext().isRemote()).isTrue();
        assertThat(pricingServer.getParentSpanId()).isEqualTo(inventorySpanId);
        assertThat(pricingServer.getParentSpanContext().isRemote()).isTrue();

        for (Service service : services) {
            for (SpanData span : service.exporter.getFinishedSpanItems()) {
                assertThat(span.getTraceId()).isEqualTo(CLIENT_TRACE_ID);
                assertThat(span.getResource().getAttribute(SERVICE_NAME)).isEqualTo(service.name);
            }
        }
        assertThat(storefrontServer.getAttributes().get(SESSION_ID)).isEqualTo("s-77");
        assertThat(storefrontServer.getAttributes().get(CUSTOMER_TIER)).isNull();
        assertThat(inventoryServer.getAttributes().get(CUSTOMER_TIER)).isEqualTo("gold");
        assertThat(pricingServer.getAttributes().get(CUSTOMER_TIER)).isEqualTo("gold");
        assertThat(pricingServer.getAttributes().get(SESSION_ID)).isEqualTo("s-77");
    }

    @Test
    void unsampledRequestIsForwardedButNeverExported() {
        Service storefront = service("storefront", TextMapPropagator.composite(
                W3CTraceContextPropagator.getInstance(),
                B3Propagator.injectingMultiHeaders()));
        Service pricing = service("pricing", B3Propagator.injectingMultiHeaders());

        Map<String, String> clientRequest = new LinkedHashMap<>();
        clientRequest.put("traceparent", "00-" + CLIENT_TRACE_ID + "-" + CLIENT_SPAN_ID + "-00");

        Map<String, String> pricingRequest = storefront.handle(clientRequest, "GET /cart", Baggage::current);

        assertThat(pricingRequest)
                .containsEntry("X-B3-TraceId", CLIENT_TRACE_ID)
                .containsEntry("X-B3-Sampled", "0")
                .containsEntry("traceparent", "00-" + CLIENT_TRACE_ID + "-"
                        + pricingRequest.get("X-B3-SpanId") + "-00");

        Map<String, String> pricingResponse = pricing.handle(pricingRequest, "quote", Baggage::current);

        assertThat(pricingResponse)
                .containsEntry("X-B3-TraceId", CLIENT_TRACE_ID)
                .containsEntry("X-B3-Sampled", "0");
        assertThat(pricingResponse.get("X-B3-SpanId")).isNotEqualTo(pricingRequest.get("X-B3-SpanId"));
        assertThat(storefront.exporter.getFinishedSpanItems()).isEmpty();
        assertThat(pricing.exporter.getFinishedSpanItems()).isEmpty();
    }

    private Service service(String name, TextMapPropagator propagator) {
        Service service = new Service(name, propagator);
        services.add(service);
        return service;
    }

    /// Mimics an ingress that forwards only the headers whose names start with an allowed prefix.
    private static Map<String, String> forwardedHeaders(Map<String, String> headers, String... prefixes) {
        Map<String, String> forwarded = new LinkedHashMap<>();
        headers.forEach((key, value) -> {
            for (String prefix : prefixes) {
                if (key.startsWith(prefix)) {
                    forwarded.put(key, value);
                    return;
                }
            }
        });
        return forwarded;
    }

    /// Supplies the baggage a service attaches to the context before calling the next service.
    private interface BaggageAugmenter {
        Baggage augment();
    }

    /// One deployed service: its own SDK, propagators and in-memory exporter.
    private static final class Service {

        private final String name;

        private final InMemorySpanExporter exporter = InMemorySpanExporter.create();

        private final OpenTelemetrySdk sdk;

        private final Tracer tracer;

        Service(String name, TextMapPropagator propagator) {
            this.name = name;
            this.sdk = OpenTelemetrySdk.builder()
                    .setTracerProvider(SdkTracerProvider.builder()
                            .setResource(Resource.getDefault().merge(
                                    Resource.create(Attributes.of(SERVICE_NAME, name))))
                            .setSampler(Sampler.parentBased(Sampler.alwaysOn()))
                            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                            .build())
                    .setPropagators(ContextPropagators.create(propagator))
                    .build();
            this.tracer = sdk.getTracer("shop." + name, "1.0.0");
        }

        /// Serves one incoming request and returns the headers of the outgoing downstream call.
        Map<String, String> handle(Map<String, String> incoming, String operation, BaggageAugmenter augmenter) {
            TextMapPropagator propagator = sdk.getPropagators().getTextMapPropagator();
            Context extracted = propagator.extract(Context.root(), incoming, HEADER_GETTER);
            Span server = tracer.spanBuilder(operation)
                    .setParent(extracted)
                    .setSpanKind(SpanKind.SERVER)
                    .startSpan();
            Map<String, String> outgoing = new LinkedHashMap<>();
            try (Scope ignored = extracted.with(server).makeCurrent()) {
                Baggage.current().forEach((key, entry) -> server.setAttribute(key, entry.getValue()));
                try (Scope withBaggage = augmenter.augment().makeCurrent()) {
                    Span client = tracer.spanBuilder("call downstream")
                            .setSpanKind(SpanKind.CLIENT)
                            .startSpan();
                    try (Scope clientScope = client.makeCurrent()) {
                        SpanContext clientContext = client.getSpanContext();
                        assertThat(clientContext.getTraceId()).isEqualTo(server.getSpanContext().getTraceId());
                        propagator.inject(Context.current(), outgoing, HEADER_SETTER);
                    } finally {
                        client.end();
                    }
                }
            } finally {
                server.end();
            }
            return outgoing;
        }

        SpanData exportedServerSpan() {
            return exportedSpan(SpanKind.SERVER);
        }

        SpanData exportedClientSpan() {
            return exportedSpan(SpanKind.CLIENT);
        }

        private SpanData exportedSpan(SpanKind kind) {
            List<SpanData> spans = exporter.getFinishedSpanItems();
            assertThat(spans).filteredOn(span -> span.getKind() == kind).hasSize(1);
            return spans.stream().filter(span -> span.getKind() == kind).findFirst().orElseThrow();
        }
    }
}
