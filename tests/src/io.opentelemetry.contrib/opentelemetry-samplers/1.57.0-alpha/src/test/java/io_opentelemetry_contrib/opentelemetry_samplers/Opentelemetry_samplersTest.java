/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_contrib.opentelemetry_samplers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.contrib.sampler.LinksBasedSampler;
import io.opentelemetry.contrib.sampler.RuleBasedRoutingSampler;
import io.opentelemetry.contrib.sampler.RuleBasedRoutingSamplerBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

public class Opentelemetry_samplersTest {
    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";
    private static final String SAMPLED_SPAN_ID = "0123456789abcdef";
    private static final String UNSAMPLED_SPAN_ID = "fedcba9876543210";
    private static final AttributeKey<String> HTTP_ROUTE = AttributeKey.stringKey("http.route");
    private static final AttributeKey<String> THREAD_NAME = AttributeKey.stringKey("thread.name");

    @Test
    void linksBasedSamplerUsesLinksBeforeRootSampler() {
        Sampler samplerWithDroppingRoot = LinksBasedSampler.create(Sampler.alwaysOff());

        SamplingResult noLinksDecision =
                sample(samplerWithDroppingRoot, Attributes.empty(), List.of());
        SamplingResult sampledLinkDecision = sample(
                samplerWithDroppingRoot,
                Attributes.empty(),
                List.of(LinkData.create(spanContext(true))));
        SamplingResult unsampledLinkDecision = sample(
                LinksBasedSampler.create(Sampler.alwaysOn()),
                Attributes.empty(),
                List.of(LinkData.create(spanContext(false))));

        assertThat(noLinksDecision.getDecision()).isEqualTo(SamplingDecision.DROP);
        assertThat(sampledLinkDecision.getDecision()).isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
        assertThat(unsampledLinkDecision.getDecision()).isEqualTo(SamplingDecision.DROP);
        assertThat(samplerWithDroppingRoot.getDescription())
                .contains("LinksBased")
                .contains(Sampler.alwaysOff().getDescription());
        assertThat(samplerWithDroppingRoot).hasToString(samplerWithDroppingRoot.getDescription());
    }

    @Test
    void ruleBasedRoutingSamplerAppliesRulesInOrderForMatchingSpanKind() {
        Sampler sampler = RuleBasedRoutingSampler.builder(SpanKind.SERVER, Sampler.alwaysOff())
                .drop(HTTP_ROUTE, "/health")
                .recordAndSample(HTTP_ROUTE, "/checkout/.*")
                .build();

        assertThat(sample(sampler, Attributes.of(HTTP_ROUTE, "/checkout/42"), List.of())
                        .getDecision())
                .isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
        assertThat(sample(sampler, Attributes.of(HTTP_ROUTE, "/health"), List.of()).getDecision())
                .isEqualTo(SamplingDecision.DROP);
        assertThat(sample(sampler, Attributes.of(HTTP_ROUTE, "/other"), List.of()).getDecision())
                .isEqualTo(SamplingDecision.DROP);
        assertThat(sampleClientSpan(sampler, Attributes.of(HTTP_ROUTE, "/checkout/42"))
                        .getDecision())
                .isEqualTo(SamplingDecision.DROP);
        assertThat(sampler.getDescription())
                .contains("RuleBasedRoutingSampler")
                .contains("http.route")
                .contains("SERVER");
        assertThat(sampler).hasToString(sampler.getDescription());
    }

    @Test
    void ruleBasedRoutingSamplerCanRouteByThreadNameAndCustomDelegates() {
        String currentThreadNamePattern = Pattern.quote(Thread.currentThread().getName());
        Sampler sampler = RuleBasedRoutingSampler.builder(SpanKind.INTERNAL, Sampler.alwaysOff())
                .customize(THREAD_NAME, currentThreadNamePattern, Sampler.alwaysOn())
                .build();

        SamplingResult decision = sampler.shouldSample(
                Context.root(),
                TRACE_ID,
                "thread-routed",
                SpanKind.INTERNAL,
                Attributes.empty(),
                List.of());

        assertThat(decision.getDecision()).isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
    }

    @Test
    void ruleBasedRoutingBuilderRejectsNullArguments() {
        assertThatNullPointerException()
                .isThrownBy(() -> RuleBasedRoutingSampler.builder(null, Sampler.alwaysOn()))
                .withMessageContaining("span kind");
        assertThatNullPointerException()
                .isThrownBy(() -> RuleBasedRoutingSampler.builder(SpanKind.SERVER, null))
                .withMessageContaining("fallback sampler");

        RuleBasedRoutingSamplerBuilder builder =
                RuleBasedRoutingSampler.builder(SpanKind.SERVER, Sampler.alwaysOn());
        assertThatNullPointerException()
                .isThrownBy(() -> builder.recordAndSample(null, ".*"))
                .withMessageContaining("attributeKey");
        assertThatNullPointerException()
                .isThrownBy(() -> builder.drop(HTTP_ROUTE, null))
                .withMessageContaining("pattern");
        assertThatNullPointerException()
                .isThrownBy(() -> builder.customize(HTTP_ROUTE, ".*", null))
                .withMessageContaining("sampler");
    }

    @Test
    void autoconfigureLoadsLinksBasedSamplerProviderFromServiceConfiguration() {
        AtomicReference<Sampler> configuredSampler = new AtomicReference<>();
        AutoConfiguredOpenTelemetrySdk configuredSdk = AutoConfiguredOpenTelemetrySdk.builder()
                .disableShutdownHook()
                .addPropertiesSupplier(Opentelemetry_samplersTest::autoconfigureProperties)
                .addSamplerCustomizer((Sampler sampler, ConfigProperties config) -> {
                    configuredSampler.set(sampler);
                    return sampler;
                })
                .build();
        OpenTelemetrySdk sdk = configuredSdk.getOpenTelemetrySdk();
        try {
            Span sampledLinkSpan = sdk.getTracer("sampler-test")
                    .spanBuilder("sampled-link")
                    .setNoParent()
                    .addLink(spanContext(true))
                    .startSpan();
            Span unsampledLinkSpan = sdk.getTracer("sampler-test")
                    .spanBuilder("unsampled-link")
                    .setNoParent()
                    .addLink(spanContext(false))
                    .startSpan();
            Span rootSpan = sdk.getTracer("sampler-test")
                    .spanBuilder("root")
                    .setNoParent()
                    .startSpan();
            try {
                assertThat(configuredSampler.get()).isNotNull();
                assertThat(configuredSampler.get().getDescription()).contains("LinksBased");
                assertThat(sampledLinkSpan.isRecording()).isTrue();
                assertThat(unsampledLinkSpan.isRecording()).isFalse();
                assertThat(rootSpan.isRecording()).isTrue();
            } finally {
                sampledLinkSpan.end();
                unsampledLinkSpan.end();
                rootSpan.end();
            }
        } finally {
            sdk.close();
        }
    }

    @Test
    void declarativeConfigurationLoadsRuleBasedRoutingSamplerProviderFromYaml() {
        String yaml = """
                rule_based_routing:
                  fallback_sampler:
                    always_off: {}
                  span_kind: SERVER
                  rules:
                    - attribute: http.route
                      pattern: /checkout/.*
                      action: RECORD_AND_SAMPLE
                    - attribute: http.route
                      pattern: /internal/.*
                      action: DROP
                """;
        Sampler sampler = DeclarativeConfiguration.createSampler(
                DeclarativeConfiguration.toConfigProperties(
                        new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))));

        assertThat(sample(sampler, Attributes.of(HTTP_ROUTE, "/checkout/42"), List.of())
                        .getDecision())
                .isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
        assertThat(sample(sampler, Attributes.of(HTTP_ROUTE, "/internal/status"), List.of())
                        .getDecision())
                .isEqualTo(SamplingDecision.DROP);
        assertThat(sample(sampler, Attributes.of(HTTP_ROUTE, "/public"), List.of()).getDecision())
                .isEqualTo(SamplingDecision.DROP);
        assertThat(sampleClientSpan(sampler, Attributes.of(HTTP_ROUTE, "/checkout/42"))
                        .getDecision())
                .isEqualTo(SamplingDecision.DROP);
        assertThat(sampler.getDescription())
                .contains("RuleBasedRoutingSampler")
                .contains("http.route")
                .contains("/checkout/.*");
    }

    private static Map<String, String> autoconfigureProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("otel.traces.sampler", "linksbased_parentbased_always_on");
        properties.put("otel.traces.exporter", "none");
        properties.put("otel.metrics.exporter", "none");
        properties.put("otel.logs.exporter", "none");
        return properties;
    }

    private static SamplingResult sample(
            Sampler sampler, Attributes attributes, List<LinkData> links) {
        return sampler.shouldSample(
                Context.root(), TRACE_ID, "operation", SpanKind.SERVER, attributes, links);
    }

    private static SamplingResult sampleClientSpan(Sampler sampler, Attributes attributes) {
        return sampler.shouldSample(
                Context.root(),
                TRACE_ID,
                "client-operation",
                SpanKind.CLIENT,
                attributes,
                Collections.emptyList());
    }

    private static SpanContext spanContext(boolean sampled) {
        return SpanContext.create(
                TRACE_ID,
                sampled ? SAMPLED_SPAN_ID : UNSAMPLED_SPAN_ID,
                sampled ? TraceFlags.getSampled() : TraceFlags.getDefault(),
                TraceState.getDefault());
    }
}
