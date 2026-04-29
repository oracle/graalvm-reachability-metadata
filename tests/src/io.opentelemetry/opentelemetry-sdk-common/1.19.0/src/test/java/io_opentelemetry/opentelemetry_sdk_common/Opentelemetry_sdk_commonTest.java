/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_sdk_common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.common.InstrumentationScopeInfoBuilder;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class Opentelemetry_sdk_commonTest {
    private static final AttributeKey<String> STRING_KEY = AttributeKey.stringKey("library.name");
    private static final AttributeKey<Boolean> BOOLEAN_KEY = AttributeKey.booleanKey("feature.enabled");
    private static final AttributeKey<Long> LONG_KEY = AttributeKey.longKey("worker.count");
    private static final AttributeKey<Double> DOUBLE_KEY = AttributeKey.doubleKey("ratio");
    private static final AttributeKey<List<String>> STRING_ARRAY_KEY = AttributeKey.stringArrayKey("zones");
    private static final AttributeKey<List<Boolean>> BOOLEAN_ARRAY_KEY = AttributeKey.booleanArrayKey("flags");
    private static final AttributeKey<List<Long>> LONG_ARRAY_KEY = AttributeKey.longArrayKey("ports");
    private static final AttributeKey<List<Double>> DOUBLE_ARRAY_KEY = AttributeKey.doubleArrayKey("percentiles");

    @Test
    void defaultClockProvidesWallClockAndMonotonicTime() {
        Clock clock = Clock.getDefault();

        long wallTimeBefore = System.currentTimeMillis();
        long clockWallTime = TimeUnit.NANOSECONDS.toMillis(clock.now());
        long wallTimeAfter = System.currentTimeMillis();
        long nanoTimeBefore = clock.nanoTime();
        long nanoTimeAfter = clock.nanoTime();

        assertThat(clock).isSameAs(Clock.getDefault());
        assertThat(clockWallTime).isBetween(wallTimeBefore - 1_000L, wallTimeAfter + 1_000L);
        assertThat(nanoTimeAfter).isGreaterThanOrEqualTo(nanoTimeBefore);
    }

    @Test
    void completableResultCodeReportsTerminalStateAndRunsCallbacksOnce() {
        CompletableResultCode resultCode = new CompletableResultCode();
        AtomicInteger callbacks = new AtomicInteger();

        CompletableResultCode returnedFromRegistration = resultCode.whenComplete(callbacks::incrementAndGet);

        assertThat(returnedFromRegistration).isSameAs(resultCode);
        assertThat(resultCode.isDone()).isFalse();
        assertThat(resultCode.isSuccess()).isFalse();
        assertThat(callbacks.get()).isZero();

        CompletableResultCode returnedFromSuccess = resultCode.succeed();

        assertThat(returnedFromSuccess).isSameAs(resultCode);
        assertThat(resultCode.isDone()).isTrue();
        assertThat(resultCode.isSuccess()).isTrue();
        assertThat(callbacks.get()).isEqualTo(1);

        resultCode.fail().succeed().whenComplete(callbacks::incrementAndGet);

        assertThat(resultCode.isDone()).isTrue();
        assertThat(resultCode.isSuccess()).isTrue();
        assertThat(callbacks.get()).isEqualTo(2);
    }

    @Test
    void predefinedCompletableResultCodesAreAlreadyCompleted() {
        AtomicInteger successCallbacks = new AtomicInteger();
        AtomicInteger failureCallbacks = new AtomicInteger();

        CompletableResultCode success = CompletableResultCode.ofSuccess()
                .whenComplete(successCallbacks::incrementAndGet);
        CompletableResultCode failure = CompletableResultCode.ofFailure()
                .whenComplete(failureCallbacks::incrementAndGet);

        assertThat(success.isDone()).isTrue();
        assertThat(success.isSuccess()).isTrue();
        assertThat(failure.isDone()).isTrue();
        assertThat(failure.isSuccess()).isFalse();
        assertThat(successCallbacks.get()).isEqualTo(1);
        assertThat(failureCallbacks.get()).isEqualTo(1);
    }

    @Test
    void combinedCompletableResultCodeWaitsForAllInputsAndPropagatesFailure() {
        CompletableResultCode first = new CompletableResultCode();
        CompletableResultCode second = new CompletableResultCode();
        CompletableResultCode combined = CompletableResultCode.ofAll(Arrays.asList(first, second));
        AtomicInteger callbacks = new AtomicInteger();
        combined.whenComplete(callbacks::incrementAndGet);

        first.succeed();

        assertThat(combined.isDone()).isFalse();
        assertThat(combined.isSuccess()).isFalse();
        assertThat(callbacks.get()).isZero();

        second.fail();
        combined.join(1, TimeUnit.SECONDS);

        assertThat(combined.isDone()).isTrue();
        assertThat(combined.isSuccess()).isFalse();
        assertThat(callbacks.get()).isEqualTo(1);
    }

    @Test
    void combinedCompletableResultCodeSucceedsForEmptyOrSuccessfulInputs() {
        CompletableResultCode first = new CompletableResultCode();
        CompletableResultCode second = new CompletableResultCode();
        CompletableResultCode combined = CompletableResultCode.ofAll(Arrays.asList(first, second));

        second.succeed();
        first.succeed();
        combined.join(1, TimeUnit.SECONDS);

        assertThat(combined.isDone()).isTrue();
        assertThat(combined.isSuccess()).isTrue();
        assertThat(CompletableResultCode.ofAll(Collections.emptyList()).isDone()).isTrue();
        assertThat(CompletableResultCode.ofAll(Collections.emptyList()).isSuccess()).isTrue();
    }

    @Test
    void completableResultCodeJoinWaitsForAsyncCompletionAndLeavesPendingCodeIncompleteOnTimeout()
            throws InterruptedException {
        CompletableResultCode pending = new CompletableResultCode();

        CompletableResultCode returnedFromTimedOutJoin = pending.join(1, TimeUnit.MILLISECONDS);

        assertThat(returnedFromTimedOutJoin).isSameAs(pending);
        assertThat(pending.isDone()).isFalse();
        assertThat(pending.isSuccess()).isFalse();

        CompletableResultCode asyncResult = new CompletableResultCode();
        CountDownLatch completerStarted = new CountDownLatch(1);
        Thread completer = new Thread(() -> {
            completerStarted.countDown();
            try {
                Thread.sleep(50L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                asyncResult.fail();
                return;
            }
            asyncResult.succeed();
        });
        completer.start();
        assertThat(completerStarted.await(1, TimeUnit.SECONDS)).isTrue();

        CompletableResultCode returnedFromCompletedJoin = asyncResult.join(5, TimeUnit.SECONDS);
        completer.join(1_000L);

        assertThat(returnedFromCompletedJoin).isSameAs(asyncResult);
        assertThat(asyncResult.isDone()).isTrue();
        assertThat(asyncResult.isSuccess()).isTrue();
        assertThat(completer.isAlive()).isFalse();
    }

    @Test
    void instrumentationScopeInfoSupportsFactoryMethodsAndBuilderAttributes() {
        Attributes attributes = Attributes.of(STRING_KEY, "scope-test", BOOLEAN_KEY, true);

        InstrumentationScopeInfo nameOnly = InstrumentationScopeInfo.create("scope-name");
        InstrumentationScopeInfo fullScope = InstrumentationScopeInfo.builder("scope-name")
                .setVersion("2.0.0")
                .setSchemaUrl("https://schemas.example/scope")
                .build();
        InstrumentationScopeInfoBuilder builder = InstrumentationScopeInfo.builder("builder-scope");
        InstrumentationScopeInfo built = builder
                .setVersion("3.0.0")
                .setSchemaUrl("https://schemas.example/builder")
                .setAttributes(attributes)
                .build();
        InstrumentationScopeInfo sameBuilt = InstrumentationScopeInfo.builder("builder-scope")
                .setVersion("3.0.0")
                .setSchemaUrl("https://schemas.example/builder")
                .setAttributes(attributes)
                .build();

        assertThat(nameOnly.getName()).isEqualTo("scope-name");
        assertThat(nameOnly.getVersion()).isNull();
        assertThat(nameOnly.getSchemaUrl()).isNull();
        assertThat(nameOnly.getAttributes().isEmpty()).isTrue();
        assertThat(fullScope.getName()).isEqualTo("scope-name");
        assertThat(fullScope.getVersion()).isEqualTo("2.0.0");
        assertThat(fullScope.getSchemaUrl()).isEqualTo("https://schemas.example/scope");
        assertThat(fullScope.getAttributes().isEmpty()).isTrue();
        assertThat(built.getAttributes().get(STRING_KEY)).isEqualTo("scope-test");
        assertThat(built.getAttributes().get(BOOLEAN_KEY)).isTrue();
        assertThat(built).isEqualTo(sameBuilt).hasSameHashCodeAs(sameBuilt);
        assertThat(InstrumentationScopeInfo.empty().getName()).isEmpty();
        assertThat(InstrumentationScopeInfo.empty().getAttributes().isEmpty()).isTrue();
        assertThatNullPointerException().isThrownBy(() -> InstrumentationScopeInfo.create(null));
    }

    @Test
    void resourcesCanBeCreatedFromAttributesAndSchemas() {
        Attributes attributes = Attributes.of(STRING_KEY, "resource-test", LONG_KEY, 7L, DOUBLE_KEY, 0.75D);

        Resource resource = Resource.create(attributes, "https://schemas.example/resource");

        assertThat(resource.getSchemaUrl()).isEqualTo("https://schemas.example/resource");
        assertThat(resource.getAttributes()).isSameAs(attributes);
        assertThat(resource.getAttribute(STRING_KEY)).isEqualTo("resource-test");
        assertThat(resource.getAttribute(LONG_KEY)).isEqualTo(7L);
        assertThat(resource.getAttribute(DOUBLE_KEY)).isEqualTo(0.75D);
        assertThat(Resource.empty().getAttributes().isEmpty()).isTrue();
        assertThat(Resource.empty().getSchemaUrl()).isNull();
        assertThat(Resource.empty().merge(null)).isSameAs(Resource.empty());
        assertThat(Resource.getDefault().getAttributes().isEmpty()).isFalse();
        assertThatNullPointerException().isThrownBy(() -> Resource.create(null));
    }

    @Test
    void defaultResourceProvidesRequiredSdkIdentityAttributes() {
        Resource defaultResource = Resource.getDefault();

        assertThat(defaultResource.getAttribute(AttributeKey.stringKey("service.name")))
                .startsWith("unknown_service");
        assertThat(defaultResource.getAttribute(AttributeKey.stringKey("telemetry.sdk.name")))
                .isEqualTo("opentelemetry");
        assertThat(defaultResource.getAttribute(AttributeKey.stringKey("telemetry.sdk.language")))
                .isEqualTo("java");
        assertThat(defaultResource.getAttribute(AttributeKey.stringKey("telemetry.sdk.version")))
                .isNotBlank();
    }

    @Test
    void resourceBuilderStoresPrimitiveScalarAndArrayAttributes() {
        Resource resource = Resource.builder()
                .put("service.name", "payments")
                .put("long.scalar", 42L)
                .put("double.scalar", 1.25D)
                .put("boolean.scalar", true)
                .put("string.array", "blue", "green")
                .put("long.array", 8080L, 8443L)
                .put("double.array", 0.5D, 0.95D)
                .put("boolean.array", true, false)
                .build();

        assertThat(resource.getAttribute(AttributeKey.stringKey("service.name"))).isEqualTo("payments");
        assertThat(resource.getAttribute(AttributeKey.longKey("long.scalar"))).isEqualTo(42L);
        assertThat(resource.getAttribute(AttributeKey.doubleKey("double.scalar"))).isEqualTo(1.25D);
        assertThat(resource.getAttribute(AttributeKey.booleanKey("boolean.scalar"))).isTrue();
        assertThat(resource.getAttribute(AttributeKey.stringArrayKey("string.array"))).containsExactly("blue", "green");
        assertThat(resource.getAttribute(AttributeKey.longArrayKey("long.array"))).containsExactly(8080L, 8443L);
        assertThat(resource.getAttribute(AttributeKey.doubleArrayKey("double.array"))).containsExactly(0.5D, 0.95D);
        assertThat(resource.getAttribute(AttributeKey.booleanArrayKey("boolean.array"))).containsExactly(true, false);
    }

    @Test
    void resourceBuilderStoresTypedKeysPutAllAndRemovesMatchingAttributes() {
        Resource base = Resource.builder()
                .put(STRING_KEY, "base")
                .put(BOOLEAN_KEY, true)
                .setSchemaUrl("https://schemas.example/base")
                .build();
        Attributes additionalAttributes = Attributes.builder()
                .put(LONG_KEY, 3L)
                .put(DOUBLE_KEY, 2.5D)
                .put(STRING_ARRAY_KEY, Arrays.asList("us-east", "eu-west"))
                .put(BOOLEAN_ARRAY_KEY, Arrays.asList(true, false))
                .put(LONG_ARRAY_KEY, Arrays.asList(80L, 443L))
                .put(DOUBLE_ARRAY_KEY, Arrays.asList(0.5D, 0.99D))
                .build();

        Resource resource = Resource.builder()
                .putAll(base)
                .putAll(additionalAttributes)
                .put(STRING_KEY, "override")
                .put(LONG_KEY, 9)
                .removeIf(key -> key.getKey().equals("ratio") || key.getKey().equals("feature.enabled"))
                .setSchemaUrl("https://schemas.example/builder")
                .build();

        assertThat(resource.getSchemaUrl()).isEqualTo("https://schemas.example/builder");
        assertThat(resource.getAttribute(STRING_KEY)).isEqualTo("override");
        assertThat(resource.getAttribute(LONG_KEY)).isEqualTo(9L);
        assertThat(resource.getAttribute(DOUBLE_KEY)).isNull();
        assertThat(resource.getAttribute(BOOLEAN_KEY)).isNull();
        assertThat(resource.getAttribute(STRING_ARRAY_KEY)).containsExactly("us-east", "eu-west");
        assertThat(resource.getAttribute(BOOLEAN_ARRAY_KEY)).containsExactly(true, false);
        assertThat(resource.getAttribute(LONG_ARRAY_KEY)).containsExactly(80L, 443L);
        assertThat(resource.getAttribute(DOUBLE_ARRAY_KEY)).containsExactly(0.5D, 0.99D);
    }

    @Test
    void resourceMergeCombinesAttributesOverridesConflictsAndResolvesSchemaUrls() {
        Resource left = Resource.builder()
                .put(STRING_KEY, "left")
                .put(LONG_KEY, 1L)
                .setSchemaUrl("https://schemas.example/shared")
                .build();
        Resource right = Resource.builder()
                .put(STRING_KEY, "right")
                .put(BOOLEAN_KEY, true)
                .setSchemaUrl("https://schemas.example/shared")
                .build();
        Resource sameSchemaMerged = left.merge(right);
        Resource rightSchemaMerged = Resource.create(Attributes.of(LONG_KEY, 2L)).merge(right);
        Resource leftSchemaMerged = left.merge(Resource.create(Attributes.of(DOUBLE_KEY, 4.0D)));
        Resource conflictingSchemaMerged = left.merge(right.toBuilder()
                .setSchemaUrl("https://schemas.example/other")
                .build());

        assertThat(sameSchemaMerged.getSchemaUrl()).isEqualTo("https://schemas.example/shared");
        assertThat(sameSchemaMerged.getAttribute(STRING_KEY)).isEqualTo("right");
        assertThat(sameSchemaMerged.getAttribute(LONG_KEY)).isEqualTo(1L);
        assertThat(sameSchemaMerged.getAttribute(BOOLEAN_KEY)).isTrue();
        assertThat(rightSchemaMerged.getSchemaUrl()).isEqualTo("https://schemas.example/shared");
        assertThat(rightSchemaMerged.getAttribute(LONG_KEY)).isEqualTo(2L);
        assertThat(leftSchemaMerged.getSchemaUrl()).isEqualTo("https://schemas.example/shared");
        assertThat(leftSchemaMerged.getAttribute(DOUBLE_KEY)).isEqualTo(4.0D);
        assertThat(conflictingSchemaMerged.getSchemaUrl()).isNull();
        assertThat(conflictingSchemaMerged.getAttribute(STRING_KEY)).isEqualTo("right");
    }

    @Test
    void resourceToBuilderCopiesExistingResourceAndAllowsIndependentMutation() {
        Resource original = Resource.builder()
                .put(STRING_KEY, "original")
                .put(BOOLEAN_KEY, true)
                .setSchemaUrl("https://schemas.example/original")
                .build();

        Resource copy = original.toBuilder()
                .put(STRING_KEY, "copy")
                .removeIf(key -> key.equals(BOOLEAN_KEY))
                .build();

        assertThat(copy.getSchemaUrl()).isEqualTo("https://schemas.example/original");
        assertThat(copy.getAttribute(STRING_KEY)).isEqualTo("copy");
        assertThat(copy.getAttribute(BOOLEAN_KEY)).isNull();
        assertThat(original.getAttribute(STRING_KEY)).isEqualTo("original");
        assertThat(original.getAttribute(BOOLEAN_KEY)).isTrue();
    }

    @Test
    void resourceBuilderIgnoresNullKeysAndNullValuesWhereDocumentedByThePublicApi() {
        ResourceBuilder builder = Resource.builder();
        List<String> visitedKeys = new ArrayList<>();

        assertThatCode(() -> builder
                .put((String) null, "ignored")
                .put("ignored.null.string", (String) null)
                .put((String) null, 1L)
                .put((String) null, 1.0D)
                .put((String) null, true)
                .put("ignored.null.string.array", (String[]) null)
                .put("ignored.null.long.array", (long[]) null)
                .put("ignored.null.double.array", (double[]) null)
                .put("ignored.null.boolean.array", (boolean[]) null)
                .put((AttributeKey<String>) null, "ignored")
                .put(AttributeKey.stringKey("ignored.null.typed.value"), null)
                .putAll((Attributes) null)
                .putAll((Resource) null))
                .doesNotThrowAnyException();

        Resource resource = builder.put("kept", "value").build();
        resource.getAttributes().forEach((key, value) -> visitedKeys.add(key.getKey()));

        assertThat(resource.getAttributes().size()).isEqualTo(1);
        assertThat(resource.getAttribute(AttributeKey.stringKey("kept"))).isEqualTo("value");
        assertThat(visitedKeys).containsExactly("kept");
    }
}
