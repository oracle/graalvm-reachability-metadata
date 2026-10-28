/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises asynchronous in-process context propagation: `Context.taskWrapping` executors,
 * `Context` wrappers for `Runnable`, `Callable`, `Supplier` and `Function` stages of a
 * `CompletableFuture`, and application `ContextKey` values, deciding how SDK spans started on
 * worker threads are parented, plus producer and consumer spans joined by span links.
 *
 * A fulfilment service handles an order inside a server span that carries the tenant in an
 * application context key. It picks items in parallel on a context-wrapping pool, prices the
 * order through a `CompletableFuture` chain whose stages are wrapped with the current context,
 * and publishes a shipment message per parcel; a dispatcher thread drains the parcels and
 * records one consumer span linked to every producer span. A housekeeping job submitted to a
 * raw pool without the context starts its own trace, and no worker thread keeps the request
 * context once its task returns.
 */
class FulfilmentAsyncContextTest {

    private static final String SCOPE_NAME = "warehouse.fulfilment";

    private static final ContextKey<String> TENANT = ContextKey.named("tenant");

    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");

    private static final AttributeKey<String> TENANT_ATTRIBUTE = AttributeKey.stringKey("app.tenant");

    private static final AttributeKey<String> SKU = AttributeKey.stringKey("inventory.sku");

    private static final AttributeKey<String> PARCEL = AttributeKey.stringKey("parcel.id");

    private static final AttributeKey<Long> BATCH_SIZE =
            AttributeKey.longKey("messaging.batch.message_count");

    private InMemorySpanExporter exporter;

    private OpenTelemetrySdk openTelemetry;

    private Tracer tracer;

    private ExecutorService rawPool;

    private ExecutorService pickingPool;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .setResource(Resource.getDefault().merge(
                                Resource.create(Attributes.of(SERVICE_NAME, "fulfilment-service"))))
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .build();
        tracer = openTelemetry.getTracer(SCOPE_NAME, "3.1.0");
        rawPool = Executors.newFixedThreadPool(2);
        pickingPool = Context.taskWrapping(Executors.newFixedThreadPool(3));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        pickingPool.shutdownNow();
        rawPool.shutdownNow();
        assertThat(pickingPool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(rawPool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        openTelemetry.getSdkTracerProvider().shutdown().join(10, TimeUnit.SECONDS);
    }

    @Test
    void workerSpansFollowTheRequestContextOnlyWhereTheContextIsCarried() throws Exception {
        Span request = tracer.spanBuilder("POST /orders/{id}/fulfil")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();
        SpanContext requestContext = request.getSpanContext();
        Context requestScoped = Context.current().with(request).with(TENANT, "acme");

        List<String> picked;
        long totalCents;
        SpanContext housekeepingContext;
        try (Scope ignored = requestScoped.makeCurrent()) {
            picked = pickItems(List.of("SKU-100", "SKU-200", "SKU-300"));
            totalCents = priceOrder(picked);
            Runnable audit = Context.current().wrap(this::auditPicks);
            rawPool.submit(audit).get(10, TimeUnit.SECONDS);
            housekeepingContext = rawPool.submit(this::compactBins).get(10, TimeUnit.SECONDS);
        } finally {
            request.end();
        }

        assertThat(picked).containsExactly("SKU-100:picked", "SKU-200:picked", "SKU-300:picked");
        assertThat(totalCents).isEqualTo(3 * 1250 + 499);
        assertThat(Context.current().get(TENANT)).isNull();
        assertThat(Span.current().getSpanContext().isValid()).isFalse();

        Map<String, List<SpanData>> byName = spansByName();
        SpanData requestSpan = byName.get("POST /orders/{id}/fulfil").get(0);
        assertThat(requestSpan.getParentSpanContext().isValid()).isFalse();

        List<SpanData> picks = byName.get("pick item");
        assertThat(picks).hasSize(3);
        assertThat(picks).allSatisfy(pick -> {
            assertThat(pick.getTraceId()).isEqualTo(requestContext.getTraceId());
            assertThat(pick.getParentSpanId()).isEqualTo(requestContext.getSpanId());
            assertThat(pick.getAttributes().get(TENANT_ATTRIBUTE)).isEqualTo("acme");
            assertThat(pick.getKind()).isEqualTo(SpanKind.INTERNAL);
        });
        assertThat(picks).extracting(pick -> pick.getAttributes().get(SKU))
                .containsExactlyInAnyOrder("SKU-100", "SKU-200", "SKU-300");

        SpanData quote = byName.get("quote shipping").get(0);
        SpanData price = byName.get("price order").get(0);
        assertThat(quote.getParentSpanId()).isEqualTo(requestContext.getSpanId());
        assertThat(price.getParentSpanId()).isEqualTo(requestContext.getSpanId());
        assertThat(price.getAttributes().get(TENANT_ATTRIBUTE)).isEqualTo("acme");
        assertThat(price.getAttributes().get(AttributeKey.longKey("order.total_cents")))
                .isEqualTo(totalCents);

        SpanData auditSpan = byName.get("audit picks").get(0);
        assertThat(auditSpan.getParentSpanId()).isEqualTo(requestContext.getSpanId());
        assertThat(auditSpan.getAttributes().get(TENANT_ATTRIBUTE)).isEqualTo("acme");

        SpanData housekeeping = byName.get("compact bins").get(0);
        assertThat(housekeeping.getSpanContext()).isEqualTo(housekeepingContext);
        assertThat(housekeeping.getTraceId()).isNotEqualTo(requestContext.getTraceId());
        assertThat(housekeeping.getParentSpanContext().isValid()).isFalse();
        assertThat(housekeeping.getAttributes().get(TENANT_ATTRIBUTE)).isEqualTo("none");

        assertThat(rawPool.submit(FulfilmentAsyncContextTest::workerTenant).get(10, TimeUnit.SECONDS))
                .isEqualTo("none");
        assertThat(pickingPool.submit(FulfilmentAsyncContextTest::workerTenant)
                .get(10, TimeUnit.SECONDS)).isEqualTo("none");
    }

    @Test
    void dispatcherConsumerSpanLinksEveryParcelProducerSpan() throws Exception {
        BlockingQueue<Parcel> outbox = new ArrayBlockingQueue<>(8);
        List<SpanContext> published = new ArrayList<>();

        Span request = tracer.spanBuilder("POST /orders/{id}/ship")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();
        try (Scope ignored = Context.current().with(request).with(TENANT, "globex").makeCurrent()) {
            for (String parcelId : List.of("P-1", "P-2", "P-3")) {
                Span publish = tracer.spanBuilder("shipments publish")
                        .setSpanKind(SpanKind.PRODUCER)
                        .setAttribute(PARCEL, parcelId)
                        .startSpan();
                published.add(publish.getSpanContext());
                outbox.put(new Parcel(parcelId, publish.getSpanContext()));
                publish.end();
            }
        } finally {
            request.end();
        }

        Callable<Integer> dispatcher = () -> {
            List<Parcel> batch = new ArrayList<>();
            outbox.drainTo(batch);
            SpanBuilder builder = tracer.spanBuilder("shipments process")
                    .setSpanKind(SpanKind.CONSUMER)
                    .setNoParent()
                    .setAttribute(BATCH_SIZE, (long) batch.size());
            for (Parcel parcel : batch) {
                builder.addLink(parcel.producer(), Attributes.of(PARCEL, parcel.id()));
            }
            Span process = builder.startSpan();
            try (Scope ignored = process.makeCurrent()) {
                for (Parcel parcel : batch) {
                    Span label = tracer.spanBuilder("print label")
                            .setAttribute(PARCEL, parcel.id())
                            .startSpan();
                    if (parcel.id().equals("P-2")) {
                        label.setStatus(StatusCode.ERROR, "printer jammed");
                    }
                    label.end();
                }
            } finally {
                process.end();
            }
            return batch.size();
        };
        Future<Integer> dispatched = rawPool.submit(dispatcher);
        assertThat(dispatched.get(10, TimeUnit.SECONDS)).isEqualTo(3);

        Map<String, List<SpanData>> byName = spansByName();
        SpanData requestSpan = byName.get("POST /orders/{id}/ship").get(0);
        List<SpanData> producers = byName.get("shipments publish");
        assertThat(producers).hasSize(3).allSatisfy(producer -> {
            assertThat(producer.getKind()).isEqualTo(SpanKind.PRODUCER);
            assertThat(producer.getParentSpanId()).isEqualTo(requestSpan.getSpanId());
        });

        SpanData process = byName.get("shipments process").get(0);
        assertThat(process.getKind()).isEqualTo(SpanKind.CONSUMER);
        assertThat(process.getParentSpanContext().isValid()).isFalse();
        assertThat(process.getTraceId()).isNotEqualTo(requestSpan.getTraceId());
        assertThat(process.getAttributes().get(BATCH_SIZE)).isEqualTo(3L);
        assertThat(process.getTotalRecordedLinks()).isEqualTo(3);
        assertThat(process.getLinks()).extracting(LinkData::getSpanContext)
                .containsExactlyElementsOf(published);
        assertThat(process.getLinks()).extracting(link -> link.getAttributes().get(PARCEL))
                .containsExactly("P-1", "P-2", "P-3");

        List<SpanData> labels = byName.get("print label");
        assertThat(labels).hasSize(3).allSatisfy(label -> {
            assertThat(label.getTraceId()).isEqualTo(process.getTraceId());
            assertThat(label.getParentSpanId()).isEqualTo(process.getSpanId());
        });
        assertThat(labels).filteredOn(label -> label.getStatus().getStatusCode() == StatusCode.ERROR)
                .extracting(label -> label.getAttributes().get(PARCEL))
                .containsExactly("P-2");
    }

    /// Picks every item in parallel on the context-wrapping pool, each in its own span.
    private List<String> pickItems(List<String> skus) throws Exception {
        List<Future<String>> picks = new ArrayList<>();
        for (String sku : skus) {
            picks.add(pickingPool.submit(() -> {
                Span pick = tracer.spanBuilder("pick item")
                        .setAttribute(SKU, sku)
                        .setAttribute(TENANT_ATTRIBUTE, workerTenant())
                        .startSpan();
                try (Scope ignored = pick.makeCurrent()) {
                    return sku + ":picked";
                } finally {
                    pick.end();
                }
            }));
        }
        List<String> results = new ArrayList<>();
        for (Future<String> pick : picks) {
            results.add(pick.get(10, TimeUnit.SECONDS));
        }
        return results;
    }

    /// Quotes shipping and prices the order on the raw pool, carrying the context in each stage.
    private long priceOrder(List<String> picked) throws ExecutionException, InterruptedException {
        Context context = Context.current();
        Function<Long, Long> addItems = context.wrapFunction(shippingCents -> {
            Span price = tracer.spanBuilder("price order")
                    .setAttribute(TENANT_ATTRIBUTE, workerTenant())
                    .startSpan();
            long total = shippingCents + picked.size() * 1250L;
            price.setAttribute("order.total_cents", total);
            price.end();
            return total;
        });
        return CompletableFuture
                .supplyAsync(context.wrapSupplier(() -> {
                    Span quote = tracer.spanBuilder("quote shipping").startSpan();
                    quote.end();
                    return 499L;
                }), rawPool)
                .thenApplyAsync(addItems, rawPool)
                .get();
    }

    /// Records the pick audit for whichever tenant the current context belongs to.
    private void auditPicks() {
        tracer.spanBuilder("audit picks")
                .setAttribute(TENANT_ATTRIBUTE, workerTenant())
                .startSpan()
                .end();
    }

    /// Housekeeping that runs without the request context, so it starts a trace of its own.
    private SpanContext compactBins() {
        Span compact = tracer.spanBuilder("compact bins")
                .setAttribute(TENANT_ATTRIBUTE, workerTenant())
                .startSpan();
        compact.end();
        return compact.getSpanContext();
    }

    private static String workerTenant() {
        String tenant = Context.current().get(TENANT);
        return tenant == null ? "none" : tenant;
    }

    private Map<String, List<SpanData>> spansByName() {
        return exporter.getFinishedSpanItems().stream()
                .collect(Collectors.groupingBy(SpanData::getName));
    }

    /// A shipment message carrying the context of the span that published it.
    private record Parcel(String id, SpanContext producer) {
    }
}
