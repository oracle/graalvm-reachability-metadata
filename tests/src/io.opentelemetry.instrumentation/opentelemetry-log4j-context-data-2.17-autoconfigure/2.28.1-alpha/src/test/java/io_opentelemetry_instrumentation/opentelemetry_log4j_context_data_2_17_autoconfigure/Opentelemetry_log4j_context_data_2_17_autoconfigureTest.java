/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_log4j_context_data_2_17_autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.log4j.contextdata.v2_17.OpenTelemetryContextDataProvider;
import java.util.Map;
import java.util.ServiceLoader;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.util.ContextDataProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class Opentelemetry_log4j_context_data_2_17_autoconfigureTest {
    private static final String TRACE_ID_KEY = "trace_id_test";
    private static final String SPAN_ID_KEY = "span_id_test";
    private static final String TRACE_FLAGS_KEY = "trace_flags_test";
    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";
    private static final String SPAN_ID = "0123456789abcdef";

    @BeforeAll
    static void configureProvider() {
        System.setProperty("otel.instrumentation.common.logging.trace-id", TRACE_ID_KEY);
        System.setProperty("otel.instrumentation.common.logging.span-id", SPAN_ID_KEY);
        System.setProperty("otel.instrumentation.common.logging.trace-flags", TRACE_FLAGS_KEY);
        System.setProperty("otel.instrumentation.log4j-context-data.add-baggage", "true");
    }

    @AfterEach
    void clearThreadContext() {
        ThreadContext.clearAll();
    }

    @AfterAll
    static void clearConfigurationProperties() {
        System.clearProperty("otel.instrumentation.common.logging.trace-id");
        System.clearProperty("otel.instrumentation.common.logging.span-id");
        System.clearProperty("otel.instrumentation.common.logging.trace-flags");
        System.clearProperty("otel.instrumentation.log4j-context-data.add-baggage");
    }

    @Test
    void discoversContextDataProviderThroughLog4jSpi() {
        boolean providerFound = false;
        for (ContextDataProvider provider : ServiceLoader.load(ContextDataProvider.class)) {
            if (provider instanceof OpenTelemetryContextDataProvider) {
                providerFound = true;
                break;
            }
        }

        assertThat(providerFound).isTrue();
    }

    @Test
    void doesNotAddTraceContextWhenThereIsNoActiveSpan() {
        OpenTelemetryContextDataProvider provider = new OpenTelemetryContextDataProvider();
        Map<String, String> contextData = provider.supplyContextData();

        assertThat(contextData)
                .doesNotContainKeys(TRACE_ID_KEY, SPAN_ID_KEY, TRACE_FLAGS_KEY, "baggage.customer");
    }

    @Test
    void suppliesConfiguredTraceKeysAndBaggageFromCurrentContext() {
        Span span = Span.wrap(sampledSpanContext());
        Baggage baggage = Baggage.builder()
                .put("customer", "acme")
                .put("region", "emea")
                .build();
        Context context = Context.current().with(span).with(baggage);

        Map<String, String> contextData;
        try (Scope scope = context.makeCurrent()) {
            contextData = new OpenTelemetryContextDataProvider().supplyContextData();
        }

        assertThat(contextData)
                .containsEntry(TRACE_ID_KEY, TRACE_ID)
                .containsEntry(SPAN_ID_KEY, SPAN_ID)
                .containsEntry(TRACE_FLAGS_KEY, TraceFlags.getSampled().asHex())
                .containsEntry("baggage.customer", "acme")
                .containsEntry("baggage.region", "emea")
                .doesNotContainKeys("trace_id", "span_id", "trace_flags", "customer", "region");
    }

    @Test
    void leavesAlreadyInstrumentedLog4jThreadContextUntouched() {
        ThreadContext.put(TRACE_ID_KEY, "existing-trace-id");
        ThreadContext.put(SPAN_ID_KEY, "existing-span-id");
        ThreadContext.put(TRACE_FLAGS_KEY, "existing-trace-flags");

        Map<String, String> contextData;
        try (Scope scope = Span.wrap(sampledSpanContext()).makeCurrent()) {
            contextData = new OpenTelemetryContextDataProvider().supplyContextData();
        }

        assertThat(contextData)
                .doesNotContainKeys(TRACE_ID_KEY, SPAN_ID_KEY, TRACE_FLAGS_KEY, "baggage.customer");
        assertThat(ThreadContext.get(TRACE_ID_KEY)).isEqualTo("existing-trace-id");
        assertThat(ThreadContext.get(SPAN_ID_KEY)).isEqualTo("existing-span-id");
        assertThat(ThreadContext.get(TRACE_FLAGS_KEY)).isEqualTo("existing-trace-flags");
    }

    @Test
    void addsOpenTelemetryContextDataToLog4jEvents() {
        String loggerName = getClass().getName() + ".otel-context-event";
        CapturingAppender appender = new CapturingAppender("otel-context-appender");
        LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = loggerContext.getConfiguration();
        LoggerConfig loggerConfig = new LoggerConfig(loggerName, Level.INFO, false);
        loggerConfig.addAppender(appender, Level.INFO, null);
        appender.start();
        configuration.addAppender(appender);
        configuration.addLogger(loggerName, loggerConfig);
        loggerContext.updateLoggers();

        try (Scope scope = Span.wrap(sampledSpanContext()).makeCurrent()) {
            LogManager.getLogger(loggerName).info("captured");
        } finally {
            configuration.removeLogger(loggerName);
            loggerConfig.removeAppender(appender.getName());
            appender.stop();
            loggerContext.updateLoggers();
        }

        LogEvent event = appender.getLastEvent();
        assertThat(event).isNotNull();
        assertThat(event.getContextData().toMap())
                .containsEntry(TRACE_ID_KEY, TRACE_ID)
                .containsEntry(SPAN_ID_KEY, SPAN_ID)
                .containsEntry(TRACE_FLAGS_KEY, TraceFlags.getSampled().asHex());
    }

    private static SpanContext sampledSpanContext() {
        return SpanContext.create(
                TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    }

    private static final class CapturingAppender extends AbstractAppender {
        private LogEvent lastEvent;

        private CapturingAppender(String name) {
            super(name, null, null, false, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            lastEvent = event.toImmutable();
        }

        private LogEvent getLastEvent() {
            return lastEvent;
        }
    }
}
