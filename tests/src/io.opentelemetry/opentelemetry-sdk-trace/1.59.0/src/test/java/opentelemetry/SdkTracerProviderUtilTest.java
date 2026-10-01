/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package opentelemetry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.internal.ExceptionAttributeResolver;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.internal.SdkTracerProviderUtil;
import io.opentelemetry.sdk.trace.internal.TracerConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SdkTracerProviderUtilTest {

    @Test
    public void setTracerConfiguratorOnBuilderAppliesConfiguredTracerBehavior() {
        try (
            SdkTracerProvider tracerProvider = createProviderWithBuilderConfigurator("disabled-by-builder")
        ) {
            Assertions.assertFalse(isRecording(tracerProvider.get("disabled-by-builder")));
            Assertions.assertTrue(isRecording(tracerProvider.get("enabled-by-builder")));
        }
    }

    @Test
    public void addTracerConfiguratorConditionAppliesOnlyToMatchingScope() {
        try (SdkTracerProvider tracerProvider = createProviderWithConditionalConfigurator()) {
            Assertions.assertFalse(isRecording(tracerProvider.get("conditional-disabled")));
            Assertions.assertTrue(isRecording(tracerProvider.get("conditional-enabled")));
        }
    }

    @Test
    public void setTracerConfiguratorOnProviderUpdatesExistingTracer() {
        try (SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build()) {
            Tracer tracer = tracerProvider.get("mutable-scope");
            Assertions.assertTrue(isRecording(tracer));

            SdkTracerProviderUtil.setTracerConfigurator(
                tracerProvider,
                scopeInfo -> scopeInfo.getName().equals("mutable-scope")
                    ? TracerConfig.disabled()
                    : TracerConfig.enabled());

            Assertions.assertFalse(isRecording(tracer));
            Assertions.assertTrue(isRecording(tracerProvider.get("other-scope")));
        }
    }

    @Test
    public void setExceptionAttributeResolverOverridesRecordedExceptionAttributes() {
        CapturingSpanProcessor spanProcessor = new CapturingSpanProcessor();
        try (SdkTracerProvider tracerProvider = createProviderWithExceptionResolver(spanProcessor)) {
            Span span =
                tracerProvider.get("exception-scope").spanBuilder("exception-span").startSpan();
            span.recordException(new IllegalStateException("boom"));
            span.end();
        }

        SpanData spanData = spanProcessor.getOnlyEndedSpan();
        Assertions.assertEquals(1, spanData.getEvents().size());

        EventData exceptionEvent = spanData.getEvents().get(0);
        Assertions.assertEquals("exception", exceptionEvent.getName());
        Assertions.assertEquals(
            "custom.exception.type",
            exceptionEvent.getAttributes().get(ExceptionAttributeResolver.EXCEPTION_TYPE));
        Assertions.assertEquals(
            "custom.exception.message",
            exceptionEvent.getAttributes().get(ExceptionAttributeResolver.EXCEPTION_MESSAGE));
        Assertions.assertEquals(
            "custom.exception.stacktrace",
            exceptionEvent.getAttributes().get(ExceptionAttributeResolver.EXCEPTION_STACKTRACE));
    }

    private static SdkTracerProvider createProviderWithBuilderConfigurator(
        String disabledScopeName
    ) {
        SdkTracerProviderBuilder tracerProviderBuilder = SdkTracerProvider.builder();
        SdkTracerProviderUtil.setTracerConfigurator(
            tracerProviderBuilder,
            scopeInfo -> scopeInfo.getName().equals(disabledScopeName)
                ? TracerConfig.disabled()
                : TracerConfig.enabled());
        return tracerProviderBuilder.build();
    }

    private static SdkTracerProvider createProviderWithConditionalConfigurator() {
        SdkTracerProviderBuilder tracerProviderBuilder = SdkTracerProvider.builder();
        SdkTracerProviderUtil.addTracerConfiguratorCondition(
            tracerProviderBuilder,
            scopeInfo -> scopeInfo.getName().equals("conditional-disabled"),
            TracerConfig.disabled());
        return tracerProviderBuilder.build();
    }

    private static SdkTracerProvider createProviderWithExceptionResolver(
        CapturingSpanProcessor spanProcessor
    ) {
        SdkTracerProviderBuilder tracerProviderBuilder = SdkTracerProvider.builder()
            .addSpanProcessor(spanProcessor);
        SdkTracerProviderUtil.setExceptionAttributeResolver(
            tracerProviderBuilder,
            (attributeSetter, exception, maxAttributeLength) -> {
                attributeSetter.setAttribute(
                    ExceptionAttributeResolver.EXCEPTION_TYPE, "custom.exception.type"
                );
                attributeSetter.setAttribute(
                    ExceptionAttributeResolver.EXCEPTION_MESSAGE, "custom.exception.message"
                );
                attributeSetter.setAttribute(
                    ExceptionAttributeResolver.EXCEPTION_STACKTRACE,
                    "custom.exception.stacktrace"
                );
            });
        return tracerProviderBuilder.build();
    }

    private static boolean isRecording(Tracer tracer) {
        Span span = tracer.spanBuilder("coverage-span").startSpan();
        try {
            return span.isRecording();
        } finally {
            span.end();
        }
    }

    private static final class CapturingSpanProcessor implements SpanProcessor {
        private final List<SpanData> endedSpans = new ArrayList<>();

        @Override
        public void onStart(Context parentContext, ReadWriteSpan span) {
        }

        @Override
        public boolean isStartRequired() {
            return false;
        }

        @Override
        public void onEnd(ReadableSpan span) {
            endedSpans.add(span.toSpanData());
        }

        @Override
        public boolean isEndRequired() {
            return true;
        }

        private SpanData getOnlyEndedSpan() {
            Assertions.assertEquals(1, endedSpans.size());
            return endedSpans.get(0);
        }
    }
}
