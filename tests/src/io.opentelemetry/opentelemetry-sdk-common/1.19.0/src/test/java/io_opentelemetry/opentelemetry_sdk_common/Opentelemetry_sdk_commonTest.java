/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk_common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class Opentelemetry_sdk_commonTest {
    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
    private static final AttributeKey<String> SERVICE_VERSION = AttributeKey.stringKey("service.version");
    private static final AttributeKey<String> HOST_NAME = AttributeKey.stringKey("host.name");
    private static final AttributeKey<Long> PID = AttributeKey.longKey("process.pid");
    private static final AttributeKey<Double> LOAD_AVERAGE = AttributeKey.doubleKey("system.cpu.load_average.1m");
    private static final AttributeKey<Boolean> DEBUG_ENABLED = AttributeKey.booleanKey("debug.enabled");
    private static final AttributeKey<List<String>> TAGS = AttributeKey.stringArrayKey("tags");
    private static final AttributeKey<List<Long>> PORTS = AttributeKey.longArrayKey("ports");
    private static final AttributeKey<List<Double>> RATIOS = AttributeKey.doubleArrayKey("ratios");
    private static final AttributeKey<List<Boolean>> FEATURE_FLAGS = AttributeKey.booleanArrayKey("feature.flags");

    @Test
    void defaultClockReturnsPlausibleWallTimeAndMonotonicTicks() {
        Clock clock = Clock.getDefault();

        long beforeEpochNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
        long firstNanoTime = clock.nanoTime();
        long nowEpochNanos = clock.now();
        long secondNanoTime = clock.nanoTime();
        long afterEpochNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());

        assertThat(clock).isSameAs(Clock.getDefault());
        assertThat(nowEpochNanos)
                .isBetween(
                        beforeEpochNanos - TimeUnit.SECONDS.toNanos(1),
                        afterEpochNanos + TimeUnit.SECONDS.toNanos(1));
        assertThat(secondNanoTime).isGreaterThanOrEqualTo(firstNanoTime);
        assertThat(clock.toString()).contains("SystemClock");
    }

    @Test
    void completableResultCodeCompletesOnceAndRunsCallbacksInOrder() {
        CompletableResultCode resultCode = new CompletableResultCode();
        AtomicInteger callbacks = new AtomicInteger();
        StringBuilder executionOrder = new StringBuilder();

        assertThat(resultCode.isDone()).isFalse();
        assertThat(resultCode.isSuccess()).isFalse();
        assertThat(resultCode.whenComplete(() -> {
            callbacks.incrementAndGet();
            executionOrder.append("first;");
        })).isSameAs(resultCode);
        resultCode.whenComplete(() -> {
            callbacks.incrementAndGet();
            executionOrder.append("second;");
        });

        assertThat(resultCode.succeed()).isSameAs(resultCode);
        resultCode.fail();
        resultCode.succeed();

        assertThat(resultCode.isDone()).isTrue();
        assertThat(resultCode.isSuccess()).isTrue();
        assertThat(callbacks).hasValue(2);
        assertThat(executionOrder).hasToString("first;second;");

        resultCode.whenComplete(() -> {
            callbacks.incrementAndGet();
            executionOrder.append("late;");
        });
        assertThat(callbacks).hasValue(3);
        assertThat(executionOrder).hasToString("first;second;late;");
    }

    @Test
    void completableResultCodeJoinAndFactoryMethodsExposeCompletionState() {
        CompletableResultCode pending = new CompletableResultCode();

        assertThat(pending.join(1, TimeUnit.MILLISECONDS)).isSameAs(pending);
        assertThat(pending.isDone()).isFalse();
        pending.fail().join(1, TimeUnit.SECONDS);
        assertThat(pending.isDone()).isTrue();
        assertThat(pending.isSuccess()).isFalse();

        assertThat(CompletableResultCode.ofSuccess().isDone()).isTrue();
        assertThat(CompletableResultCode.ofSuccess().isSuccess()).isTrue();
        assertThat(CompletableResultCode.ofFailure().isDone()).isTrue();
        assertThat(CompletableResultCode.ofFailure().isSuccess()).isFalse();
        assertThat(CompletableResultCode.ofAll(Collections.emptyList()).isSuccess()).isTrue();
    }

    @Test
    void completableResultCodeOfAllWaitsForEveryInputAndPropagatesFailure() {
        CompletableResultCode first = new CompletableResultCode();
        CompletableResultCode second = new CompletableResultCode();
        CompletableResultCode third = new CompletableResultCode();
        CompletableResultCode combined = CompletableResultCode.ofAll(Arrays.asList(first, second, third));
        AtomicInteger combinedCallbacks = new AtomicInteger();
        combined.whenComplete(combinedCallbacks::incrementAndGet);

        first.succeed();
        second.fail();
        assertThat(combined.isDone()).isFalse();
        assertThat(combinedCallbacks).hasValue(0);

        third.succeed();
        assertThat(combined.isDone()).isTrue();
        assertThat(combined.isSuccess()).isFalse();
        assertThat(combinedCallbacks).hasValue(1);

        CompletableResultCode allSuccessful = CompletableResultCode.ofAll(Arrays.asList(
                CompletableResultCode.ofSuccess(),
                CompletableResultCode.ofSuccess()));
        assertThat(allSuccessful.isDone()).isTrue();
        assertThat(allSuccessful.isSuccess()).isTrue();
    }

    @Test
    void instrumentationScopeInfoPreservesNameMetadataAndAttributes() {
        Attributes attributes = Attributes.builder()
                .put("scope.kind", "library")
                .put("scope.enabled", true)
                .build();

        InstrumentationScopeInfo scope = InstrumentationScopeInfo.builder("example.instrumentation")
                .setVersion("2.4.6")
                .setSchemaUrl("https://opentelemetry.io/schemas/1.19.0")
                .setAttributes(attributes)
                .build();

        assertThat(scope.getName()).isEqualTo("example.instrumentation");
        assertThat(scope.getVersion()).isEqualTo("2.4.6");
        assertThat(scope.getSchemaUrl()).isEqualTo("https://opentelemetry.io/schemas/1.19.0");
        assertThat(scope.getAttributes()).isEqualTo(attributes);
        assertThat(scope).isEqualTo(InstrumentationScopeInfo.builder("example.instrumentation")
                .setVersion("2.4.6")
                .setSchemaUrl("https://opentelemetry.io/schemas/1.19.0")
                .setAttributes(attributes)
                .build());
    }

    @Test
    void instrumentationScopeInfoCreateAndEmptyUseEmptyDefaults() {
        InstrumentationScopeInfo named = InstrumentationScopeInfo.create("named.scope");
        InstrumentationScopeInfo empty = InstrumentationScopeInfo.empty();

        assertThat(named.getName()).isEqualTo("named.scope");
        assertThat(named.getVersion()).isNull();
        assertThat(named.getSchemaUrl()).isNull();
        assertThat(named.getAttributes().isEmpty()).isTrue();
        assertThat(empty.getName()).isEmpty();
        assertThat(empty.getAttributes().isEmpty()).isTrue();
        assertThatThrownBy(() -> InstrumentationScopeInfo.create(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name");
    }

    @Test
    void resourceBuilderAcceptsScalarArrayAndTypedAttributes() {
        Resource resource = Resource.builder()
                .put("service.name", "checkout")
                .put("process.pid", 42L)
                .put("system.cpu.load_average.1m", 0.75D)
                .put("debug.enabled", true)
                .put("tags", "edge", "payments")
                .put("ports", 8080L, 8443L)
                .put("ratios", 0.5D, 1.5D)
                .put("feature.flags", true, false)
                .put(AttributeKey.stringKey("deployment.environment"), "test")
                .put(AttributeKey.longKey("worker.id"), 7)
                .setSchemaUrl("https://opentelemetry.io/schemas/1.19.0")
                .build();

        assertThat(resource.getSchemaUrl()).isEqualTo("https://opentelemetry.io/schemas/1.19.0");
        assertThat(resource.getAttribute(SERVICE_NAME)).isEqualTo("checkout");
        assertThat(resource.getAttribute(PID)).isEqualTo(42L);
        assertThat(resource.getAttribute(LOAD_AVERAGE)).isEqualTo(0.75D);
        assertThat(resource.getAttribute(DEBUG_ENABLED)).isTrue();
        assertThat(resource.getAttribute(TAGS)).containsExactly("edge", "payments");
        assertThat(resource.getAttribute(PORTS)).containsExactly(8080L, 8443L);
        assertThat(resource.getAttribute(RATIOS)).containsExactly(0.5D, 1.5D);
        assertThat(resource.getAttribute(FEATURE_FLAGS)).containsExactly(true, false);
        assertThat(resource.getAttribute(AttributeKey.stringKey("deployment.environment"))).isEqualTo("test");
        assertThat(resource.getAttribute(AttributeKey.longKey("worker.id"))).isEqualTo(7L);
    }

    @Test
    void resourceBuilderIgnoresNullInputsAndEmptyTypedKeys() {
        AttributeKey<String> emptyKey = AttributeKey.stringKey("");
        Resource resource = Resource.builder()
                .put((String) null, "ignored")
                .put("null.string", (String) null)
                .put("null.string.array", (String[]) null)
                .put("null.long.array", (long[]) null)
                .put("null.double.array", (double[]) null)
                .put("null.boolean.array", (boolean[]) null)
                .put((AttributeKey<String>) null, "ignored")
                .put(emptyKey, "ignored")
                .put(AttributeKey.stringKey("null.typed.value"), null)
                .putAll((Attributes) null)
                .putAll((Resource) null)
                .put(SERVICE_NAME, "checkout")
                .setSchemaUrl("https://opentelemetry.io/schemas/current")
                .build();

        assertThat(resource.getSchemaUrl()).isEqualTo("https://opentelemetry.io/schemas/current");
        assertThat(resource.getAttributes().size()).isEqualTo(1);
        assertThat(resource.getAttribute(SERVICE_NAME)).isEqualTo("checkout");
        assertThat(resource.getAttribute(emptyKey)).isNull();
        assertThat(resource.getAttribute(AttributeKey.stringKey("null.string"))).isNull();
        assertThat(resource.getAttribute(AttributeKey.stringArrayKey("null.string.array"))).isNull();
        assertThat(resource.getAttribute(AttributeKey.longArrayKey("null.long.array"))).isNull();
        assertThat(resource.getAttribute(AttributeKey.doubleArrayKey("null.double.array"))).isNull();
        assertThat(resource.getAttribute(AttributeKey.booleanArrayKey("null.boolean.array"))).isNull();
        assertThat(resource.getAttribute(AttributeKey.stringKey("null.typed.value"))).isNull();
    }

    @Test
    void resourceBuilderCopiesAndFiltersAttributesWithoutMutatingSourceResource() {
        Resource original = Resource.builder()
                .put(SERVICE_NAME, "orders")
                .put(HOST_NAME, "host-a")
                .put("drop.temporary", "remove-me")
                .setSchemaUrl("https://opentelemetry.io/schemas/original")
                .build();
        Attributes extraAttributes = Attributes.of(SERVICE_VERSION, "1.0.0");

        Resource copied = original.toBuilder()
                .putAll(extraAttributes)
                .putAll(Resource.builder().put("region", "us-east-1").build())
                .removeIf(key -> key.getKey().startsWith("drop."))
                .setSchemaUrl("https://opentelemetry.io/schemas/copied")
                .build();

        assertThat(original.getSchemaUrl()).isEqualTo("https://opentelemetry.io/schemas/original");
        assertThat(original.getAttribute(AttributeKey.stringKey("drop.temporary"))).isEqualTo("remove-me");
        assertThat(copied.getSchemaUrl()).isEqualTo("https://opentelemetry.io/schemas/copied");
        assertThat(copied.getAttribute(SERVICE_NAME)).isEqualTo("orders");
        assertThat(copied.getAttribute(HOST_NAME)).isEqualTo("host-a");
        assertThat(copied.getAttribute(SERVICE_VERSION)).isEqualTo("1.0.0");
        assertThat(copied.getAttribute(AttributeKey.stringKey("region"))).isEqualTo("us-east-1");
        assertThat(copied.getAttribute(AttributeKey.stringKey("drop.temporary"))).isNull();
    }

    @Test
    void resourceValueSemanticsIncludeSchemaUrlAndAttributes() {
        Resource first = Resource.create(Attributes.of(
                SERVICE_NAME, "orders",
                SERVICE_VERSION, "1.0.0"), "https://example.com/schemas/service");
        Resource sameValues = Resource.create(Attributes.of(
                SERVICE_NAME, "orders",
                SERVICE_VERSION, "1.0.0"), "https://example.com/schemas/service");
        Resource differentSchema = Resource.create(Attributes.of(
                SERVICE_NAME, "orders",
                SERVICE_VERSION, "1.0.0"), "https://example.com/schemas/other");
        Resource differentAttributes = Resource.create(Attributes.of(
                SERVICE_NAME, "payments",
                SERVICE_VERSION, "1.0.0"), "https://example.com/schemas/service");

        assertThat(first).isEqualTo(sameValues);
        assertThat(first.hashCode()).isEqualTo(sameValues.hashCode());
        assertThat(first).isNotEqualTo(differentSchema);
        assertThat(first).isNotEqualTo(differentAttributes);
        assertThat(first).isNotEqualTo("orders");
        assertThat(first.toString())
                .contains("https://example.com/schemas/service")
                .contains("service.name")
                .contains("orders");
    }

    @Test
    void resourceMergeUsesOtherResourceForCollisionsAndCombinesSchemaUrls() {
        Resource first = Resource.create(Attributes.of(
                SERVICE_NAME, "orders",
                HOST_NAME, "host-a"), "https://opentelemetry.io/schemas/1.19.0");
        Resource second = Resource.create(Attributes.of(
                SERVICE_NAME, "payments",
                SERVICE_VERSION, "2.1.0"), "https://opentelemetry.io/schemas/1.19.0");

        Resource merged = first.merge(second);
        Resource mergedWithNullSchema = Resource.create(Attributes.of(HOST_NAME, "host-b"))
                .merge(Resource.create(Attributes.of(SERVICE_VERSION, "3.0.0"), "schema-from-other"));
        Resource mergedWithConflictingSchema = first.merge(Resource.create(
                Attributes.of(SERVICE_VERSION, "conflicting"),
                "https://opentelemetry.io/schemas/different"));

        assertThat(merged.getSchemaUrl()).isEqualTo("https://opentelemetry.io/schemas/1.19.0");
        assertThat(merged.getAttribute(SERVICE_NAME)).isEqualTo("payments");
        assertThat(merged.getAttribute(HOST_NAME)).isEqualTo("host-a");
        assertThat(merged.getAttribute(SERVICE_VERSION)).isEqualTo("2.1.0");
        assertThat(first.merge(null)).isSameAs(first);
        assertThat(first.merge(Resource.empty())).isSameAs(first);
        assertThat(mergedWithNullSchema.getSchemaUrl()).isEqualTo("schema-from-other");
        assertThat(mergedWithConflictingSchema.getSchemaUrl()).isNull();
        assertThat(mergedWithConflictingSchema.getAttribute(SERVICE_VERSION)).isEqualTo("conflicting");
    }

    @Test
    void resourceValidatesAttributesAndExposesDefaultSdkResource() {
        Resource defaultResource = Resource.getDefault();

        assertThat(Resource.empty().getAttributes().isEmpty()).isTrue();
        assertThat(defaultResource.getAttribute(SERVICE_NAME)).isEqualTo("unknown_service:java");
        assertThat(defaultResource.getAttribute(AttributeKey.stringKey("telemetry.sdk.name")))
                .isEqualTo("opentelemetry");
        assertThat(defaultResource.getAttribute(AttributeKey.stringKey("telemetry.sdk.language")))
                .isEqualTo("java");
        assertThat(defaultResource.getAttribute(AttributeKey.stringKey("telemetry.sdk.version")))
                .isEqualTo("1.19.0");

        assertThatThrownBy(() -> Resource.create(Attributes.of(AttributeKey.stringKey("bad\nkey"), "value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Attribute key");
        assertThatThrownBy(() -> Resource.create(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("attributes");
    }
}
