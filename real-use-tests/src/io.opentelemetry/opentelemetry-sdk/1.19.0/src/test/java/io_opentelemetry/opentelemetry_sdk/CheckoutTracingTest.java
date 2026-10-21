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
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the trace SDK: an `SdkTracerProvider` that records nested spans, their attributes,
 * events, status and recorded exceptions, and hands each finished span to a `SimpleSpanProcessor`.
 *
 * A checkout service handles an order inside a server span. While that span is current it
 * reserves stock in an internal child span and charges the card in a client child span. The
 * payment gateway declines the card, so the payment span records the exception and ends with
 * an error status, while the checkout span records the decline as an event and ends with an
 * error status of its own. The exported spans share one trace, link to their parent, and carry
 * the service resource and the instrumentation scope the tracer was obtained with.
 */
class CheckoutTracingTest {

    private static final String SCOPE_NAME = "shop.checkout";

    private static final String SCOPE_VERSION = "2.4.0";

    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");

    private static final AttributeKey<String> ORDER_ID = AttributeKey.stringKey("order.id");

    private static final AttributeKey<Long> ITEM_COUNT = AttributeKey.longKey("order.item_count");

    private static final AttributeKey<Double> AMOUNT = AttributeKey.doubleKey("payment.amount");

    private static final AttributeKey<String> SKU = AttributeKey.stringKey("inventory.sku");

    private static final AttributeKey<String> REASON = AttributeKey.stringKey("decline.reason");

    private InMemorySpanExporter exporter;

    private SdkTracerProvider tracerProvider;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .setResource(Resource.getDefault().merge(
                        Resource.create(Attributes.of(SERVICE_NAME, "checkout-service"))))
                .setSampler(Sampler.parentBased(Sampler.alwaysOn()))
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
    }

    @AfterEach
    void tearDown() {
        CompletableResultCode shutdown = tracerProvider.shutdown().join(10, TimeUnit.SECONDS);
        assertThat(shutdown.isSuccess()).isTrue();
    }

    @Test
    void declinedPaymentIsTracedAsOneTraceWithErrorSpans() {
        CheckoutService service = new CheckoutService(
                tracerProvider.get(SCOPE_NAME, SCOPE_VERSION), new PaymentGateway());

        assertThatThrownBy(() -> service.checkout("order-1001", List.of("sku-red-mug", "sku-tea"), 42.5))
                .isInstanceOf(PaymentDeclinedException.class)
                .hasMessage("insufficient funds");

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).extracting(SpanData::getName)
                .containsExactly("reserve sku-red-mug", "reserve sku-tea", "charge card", "checkout");
        Map<String, SpanData> byName = spans.stream()
                .collect(Collectors.toMap(SpanData::getName, Function.identity()));

        SpanData checkout = byName.get("checkout");
        assertThat(checkout.getKind()).isEqualTo(SpanKind.SERVER);
        assertThat(checkout.getParentSpanContext().isValid()).isFalse();
        assertThat(checkout.getAttributes().get(ORDER_ID)).isEqualTo("order-1001");
        assertThat(checkout.getAttributes().get(ITEM_COUNT)).isEqualTo(2L);
        assertThat(checkout.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(checkout.getStatus().getDescription()).isEqualTo("payment declined");
        assertThat(checkout.getEvents()).extracting(EventData::getName)
                .containsExactly("stock reserved", "payment declined");
        assertThat(checkout.getEvents().get(1).getAttributes().get(REASON)).isEqualTo("insufficient funds");
        assertThat(checkout.getResource().getAttribute(SERVICE_NAME)).isEqualTo("checkout-service");
        assertThat(checkout.getInstrumentationScopeInfo().getName()).isEqualTo(SCOPE_NAME);
        assertThat(checkout.getInstrumentationScopeInfo().getVersion()).isEqualTo(SCOPE_VERSION);

        for (SpanData child : List.of(byName.get("reserve sku-red-mug"), byName.get("reserve sku-tea"),
                byName.get("charge card"))) {
            assertThat(child.getTraceId()).isEqualTo(checkout.getTraceId());
            assertThat(child.getParentSpanId()).isEqualTo(checkout.getSpanId());
            assertThat(child.hasEnded()).isTrue();
            assertThat(child.getStartEpochNanos()).isGreaterThanOrEqualTo(checkout.getStartEpochNanos());
            assertThat(child.getEndEpochNanos()).isLessThanOrEqualTo(checkout.getEndEpochNanos());
        }

        SpanData reserve = byName.get("reserve sku-tea");
        assertThat(reserve.getKind()).isEqualTo(SpanKind.INTERNAL);
        assertThat(reserve.getAttributes().get(SKU)).isEqualTo("sku-tea");
        assertThat(reserve.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);

        SpanData charge = byName.get("charge card");
        assertThat(charge.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(charge.getAttributes().get(AMOUNT)).isEqualTo(42.5);
        assertThat(charge.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(charge.getEvents()).hasSize(1);
        EventData exception = charge.getEvents().get(0);
        assertThat(exception.getName()).isEqualTo("exception");
        assertThat(exception.getAttributes().get(AttributeKey.stringKey("exception.type")))
                .isEqualTo(PaymentDeclinedException.class.getCanonicalName());
        assertThat(exception.getAttributes().get(AttributeKey.stringKey("exception.message")))
                .isEqualTo("insufficient funds");
        assertThat(exception.getAttributes().get(AttributeKey.stringKey("exception.stacktrace")))
                .contains("PaymentGateway.charge");
    }

    @Test
    void successfulCheckoutEndsWithUnsetStatusAndNoErrors() throws PaymentDeclinedException {
        CheckoutService service = new CheckoutService(
                tracerProvider.get(SCOPE_NAME, SCOPE_VERSION), new PaymentGateway());

        String receipt = service.checkout("order-1002", List.of("sku-kettle"), 19.0);

        assertThat(receipt).isEqualTo("receipt-order-1002");
        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).extracting(SpanData::getName)
                .containsExactly("reserve sku-kettle", "charge card", "checkout");
        assertThat(spans).allSatisfy(span -> {
            assertThat(span.getTraceId()).isEqualTo(spans.get(2).getTraceId());
            assertThat(span.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
        });
        assertThat(spans.get(1).getEvents()).isEmpty();
        assertThat(spans.get(2).getEvents()).extracting(EventData::getName).containsExactly("stock reserved");
        assertThat(Span.current().getSpanContext().isValid()).isFalse();
    }

    /** Raised by the payment gateway when the card issuer declines a charge. */
    static final class PaymentDeclinedException extends Exception {

        PaymentDeclinedException(String reason) {
            super(reason);
        }
    }

    /** Payment gateway that declines charges above the card limit. */
    static final class PaymentGateway {

        private static final double CARD_LIMIT = 30.0;

        void charge(double amount) throws PaymentDeclinedException {
            if (amount > CARD_LIMIT) {
                throw new PaymentDeclinedException("insufficient funds");
            }
        }
    }

    /** Checkout service instrumented with the OpenTelemetry tracing API. */
    static final class CheckoutService {

        private final Tracer tracer;

        private final PaymentGateway gateway;

        CheckoutService(Tracer tracer, PaymentGateway gateway) {
            this.tracer = tracer;
            this.gateway = gateway;
        }

        String checkout(String orderId, List<String> skus, double amount) throws PaymentDeclinedException {
            Span span = tracer.spanBuilder("checkout")
                    .setSpanKind(SpanKind.SERVER)
                    .setAttribute(ORDER_ID, orderId)
                    .setAttribute(ITEM_COUNT, (long) skus.size())
                    .startSpan();
            try (Scope ignored = span.makeCurrent()) {
                skus.forEach(this::reserve);
                span.addEvent("stock reserved");
                charge(amount);
                return "receipt-" + orderId;
            } catch (PaymentDeclinedException e) {
                span.addEvent("payment declined", Attributes.of(REASON, e.getMessage()));
                span.setStatus(StatusCode.ERROR, "payment declined");
                throw e;
            } finally {
                span.end();
            }
        }

        private void reserve(String sku) {
            Span span = tracer.spanBuilder("reserve " + sku).setAttribute(SKU, sku).startSpan();
            try (Scope ignored = span.makeCurrent()) {
                span.setStatus(StatusCode.OK);
            } finally {
                span.end();
            }
        }

        private void charge(double amount) throws PaymentDeclinedException {
            Span span = tracer.spanBuilder("charge card")
                    .setSpanKind(SpanKind.CLIENT)
                    .setAttribute(AMOUNT, amount)
                    .startSpan();
            try (Scope ignored = span.makeCurrent()) {
                gateway.charge(amount);
            } catch (PaymentDeclinedException e) {
                span.recordException(e);
                span.setStatus(StatusCode.ERROR);
                throw e;
            } finally {
                span.end();
            }
        }
    }
}
