/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises SDK autoconfiguration: `AutoConfiguredOpenTelemetrySdk` building the tracer provider,
 * meter provider, propagators, resource and exporters from `otel.*` configuration properties and
 * the service-loaded resource and propagator providers on the classpath.
 *
 * An inventory service configures OpenTelemetry the way a deployment does, through properties
 * alone: it names the service, adds resource attributes, selects the logging span and metric
 * exporters, composes W3C trace context, baggage and B3 multi-header propagators by name, caps
 * span attributes and disables the host resource provider. In code it only adds a resource
 * attribute and a second span processor. Reserving stock then produces a span the logging
 * exporter writes to `java.util.logging`, a counter the logging metric exporter writes on flush,
 * and outgoing request headers in all three propagation formats.
 */
class InventoryAutoConfigurationTest {

    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");

    private static final AttributeKey<String> SERVICE_VERSION = AttributeKey.stringKey("service.version");

    private static final AttributeKey<String> ENVIRONMENT = AttributeKey.stringKey("deployment.environment");

    private static final AttributeKey<String> TEAM = AttributeKey.stringKey("team");

    private static final AttributeKey<String> OS_TYPE = AttributeKey.stringKey("os.type");

    private static final AttributeKey<String> RUNTIME_NAME = AttributeKey.stringKey("process.runtime.name");

    private static final AttributeKey<String> HOST_NAME = AttributeKey.stringKey("host.name");

    private static final AttributeKey<String> SKU = AttributeKey.stringKey("inventory.sku");

    private static final AttributeKey<String> WAREHOUSE = AttributeKey.stringKey("inventory.warehouse");

    private static final AttributeKey<Long> QUANTITY = AttributeKey.longKey("inventory.quantity");

    private static final String SPAN_LOGGER = "io.opentelemetry.exporter.logging.LoggingSpanExporter";

    private static final String METRIC_LOGGER = "io.opentelemetry.exporter.logging.LoggingMetricExporter";

    private static final TextMapSetter<Map<String, String>> SETTER = Map::put;

    private final InMemorySpanExporter auditExporter = InMemorySpanExporter.create();

    private final RecordingHandler spanLog = new RecordingHandler();

    private final RecordingHandler metricLog = new RecordingHandler();

    /** Held strongly so the handlers stay attached: `Logger` keeps named loggers only weakly. */
    private final Logger spanLogger = Logger.getLogger(SPAN_LOGGER);

    private final Logger metricLogger = Logger.getLogger(METRIC_LOGGER);

    private AutoConfiguredOpenTelemetrySdk autoConfigured;

    @BeforeEach
    void configure() {
        spanLogger.addHandler(spanLog);
        metricLogger.addHandler(metricLog);
        autoConfigured = AutoConfiguredOpenTelemetrySdk.builder()
                .addPropertiesSupplier(InventoryAutoConfigurationTest::deploymentProperties)
                .addResourceCustomizer((resource, config) -> resource.merge(
                        Resource.builder().put(TEAM, "fulfilment").build()))
                .addTracerProviderCustomizer((builder, config) ->
                        builder.addSpanProcessor(SimpleSpanProcessor.create(auditExporter)))
                .registerShutdownHook(false)
                .setResultAsGlobal(false)
                .build();
    }

    @AfterEach
    void shutDown() {
        OpenTelemetrySdk sdk = autoConfigured.getOpenTelemetrySdk();
        sdk.getSdkTracerProvider().shutdown().join(10, TimeUnit.SECONDS);
        sdk.getSdkMeterProvider().shutdown().join(10, TimeUnit.SECONDS);
        spanLogger.removeHandler(spanLog);
        metricLogger.removeHandler(metricLog);
    }

    @Test
    void propertiesConfigureResourceExportersPropagatorsAndLimits() {
        ConfigProperties config = autoConfigured.getConfig();
        assertThat(config.getString("otel.service.name")).isEqualTo("inventory-service");
        assertThat(config.getList("otel.propagators")).containsExactly("tracecontext", "baggage", "b3multi");
        assertThat(config.getInt("otel.span.attribute.count.limit")).isEqualTo(2);

        Resource resource = autoConfigured.getResource();
        assertThat(resource.getAttribute(SERVICE_NAME)).isEqualTo("inventory-service");
        assertThat(resource.getAttribute(SERVICE_VERSION)).isEqualTo("3.1.0");
        assertThat(resource.getAttribute(ENVIRONMENT)).isEqualTo("staging");
        assertThat(resource.getAttribute(TEAM)).isEqualTo("fulfilment");
        assertThat(resource.getAttribute(OS_TYPE)).isNotBlank();
        assertThat(resource.getAttribute(RUNTIME_NAME)).isNotBlank();
        assertThat(resource.getAttribute(HOST_NAME)).isNull();

        OpenTelemetrySdk sdk = autoConfigured.getOpenTelemetrySdk();
        TextMapPropagator propagator = sdk.getPropagators().getTextMapPropagator();
        assertThat(propagator.fields()).contains("traceparent", "baggage", "X-B3-TraceId", "X-B3-SpanId");

        Map<String, String> headers = reserveStock(sdk, "SKU-1042", 3);

        assertThat(sdk.getSdkTracerProvider().forceFlush().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(sdk.getSdkMeterProvider().forceFlush().join(10, TimeUnit.SECONDS).isSuccess()).isTrue();

        List<SpanData> spans = auditExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        SpanData reservation = spans.get(0);
        assertThat(reservation.getName()).isEqualTo("reserve-stock");
        assertThat(reservation.getResource().getAttribute(TEAM)).isEqualTo("fulfilment");
        assertThat(reservation.getAttributes().size()).isEqualTo(2);
        assertThat(reservation.getTotalAttributeCount()).isEqualTo(3);
        assertThat(reservation.getAttributes().get(SKU)).isEqualTo("SKU-1042");

        String traceId = reservation.getTraceId();
        String spanId = reservation.getSpanId();
        assertThat(headers.get("traceparent")).isEqualTo("00-" + traceId + "-" + spanId + "-01");
        assertThat(headers.get("X-B3-TraceId")).isEqualTo(traceId);
        assertThat(headers.get("X-B3-SpanId")).isEqualTo(spanId);
        assertThat(headers.get("X-B3-Sampled")).isEqualTo("1");
        assertThat(headers.get("baggage")).isEqualTo("tenant=acme");

        assertThat(spanLog.messages()).anySatisfy(message -> assertThat(message)
                .contains("'reserve-stock'")
                .contains(traceId)
                .contains(spanId));
        assertThat(metricLog.messages()).anySatisfy(message -> assertThat(message)
                .contains("inventory.reservations")
                .contains("value=3"));
    }

    /** Reserves stock under a tenant's baggage and returns the headers of the outgoing call. */
    private static Map<String, String> reserveStock(OpenTelemetrySdk sdk, String sku, long quantity) {
        Tracer tracer = sdk.getTracer("inventory.reservations", "3.1.0");
        LongCounter reservations = sdk.getMeter("inventory.reservations")
                .counterBuilder("inventory.reservations")
                .setUnit("{item}")
                .build();
        Map<String, String> headers = new HashMap<>();
        Baggage baggage = Baggage.builder().put("tenant", "acme").build();
        try (Scope ignored = baggage.makeCurrent()) {
            Span span = tracer.spanBuilder("reserve-stock")
                    .setAttribute(SKU, sku)
                    .setAttribute(WAREHOUSE, "berlin-1")
                    .setAttribute(QUANTITY, quantity)
                    .startSpan();
            try (Scope spanScope = span.makeCurrent()) {
                reservations.add(quantity);
                sdk.getPropagators().getTextMapPropagator().inject(Context.current(), headers, SETTER);
            } finally {
                span.end();
            }
        }
        return headers;
    }

    private static Map<String, String> deploymentProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("otel.service.name", "inventory-service");
        properties.put("otel.resource.attributes", "deployment.environment=staging,service.version=3.1.0");
        properties.put("otel.java.disabled.resource.providers",
                "io.opentelemetry.sdk.extension.resources.HostResourceProvider");
        properties.put("otel.traces.exporter", "logging");
        properties.put("otel.metrics.exporter", "logging");
        properties.put("otel.logs.exporter", "none");
        properties.put("otel.metric.export.interval", "3600000");
        properties.put("otel.propagators", "tracecontext,baggage,b3multi");
        properties.put("otel.traces.sampler", "parentbased_always_on");
        properties.put("otel.span.attribute.count.limit", "2");
        return properties;
    }

    /** Collects the formatted messages the logging exporters write. */
    private static final class RecordingHandler extends Handler {

        private final List<String> messages = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void publish(LogRecord record) {
            String message = record.getMessage();
            Object[] parameters = record.getParameters();
            if (parameters != null) {
                for (int i = 0; i < parameters.length; i++) {
                    message = message.replace("{" + i + "}", String.valueOf(parameters[i]));
                }
            }
            messages.add(message);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        List<String> messages() {
            synchronized (messages) {
                return new ArrayList<>(messages);
            }
        }
    }
}
