/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_logback_mdc_1_0;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

public class OpenTelemetryAppenderTest {
    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";
    private static final String SPAN_ID = "0123456789abcdef";

    @Test
    void injectsSpanContextAndBaggageIntoLogbackLoggingEvent() {
        LoggerContext loggerContext = new LoggerContext();
        OpenTelemetryAppender openTelemetryAppender = new OpenTelemetryAppender();
        CapturingAppender capturingAppender = new CapturingAppender();
        try {
            Logger logger = loggerContext.getLogger("test.openTelemetryAppender");
            logger.setLevel(Level.INFO);
            logger.setAdditive(false);
            openTelemetryAppender.setContext(loggerContext);
            openTelemetryAppender.setName("openTelemetry");
            openTelemetryAppender.setTraceIdKey("test.trace_id");
            openTelemetryAppender.setSpanIdKey("test.span_id");
            openTelemetryAppender.setTraceFlagsKey("test.trace_flags");
            openTelemetryAppender.setAddBaggage(true);

            capturingAppender.setContext(loggerContext);
            capturingAppender.setName("capturing");
            capturingAppender.start();
            openTelemetryAppender.addAppender(capturingAppender);
            openTelemetryAppender.start();
            logger.addAppender(openTelemetryAppender);

            SpanContext spanContext =
                    SpanContext.create(
                            TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
            Baggage baggage = Baggage.builder().put("tenant", "testing").build();
            MDC.put("existing", "value");
            try (Scope spanScope = Span.wrap(spanContext).makeCurrent();
                    Scope baggageScope = baggage.makeCurrent()) {
                logger.info("message with telemetry context");
            } finally {
                MDC.remove("existing");
            }

            assertThat(capturingAppender.events).hasSize(1);
            ILoggingEvent event = capturingAppender.events.get(0);
            Map<String, String> mdc = event.getMDCPropertyMap();
            assertThat(mdc)
                    .containsEntry("existing", "value")
                    .containsEntry("test.trace_id", TRACE_ID)
                    .containsEntry("test.span_id", SPAN_ID)
                    .containsEntry("test.trace_flags", "01")
                    .containsEntry("baggage.tenant", "testing");
        } finally {
            openTelemetryAppender.stop();
            capturingAppender.stop();
            loggerContext.stop();
        }
    }

    private static final class CapturingAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
        private final List<ILoggingEvent> events = new ArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            events.add(event);
        }
    }
}
