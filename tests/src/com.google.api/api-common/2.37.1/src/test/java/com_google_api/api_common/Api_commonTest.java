/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_api.api_common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.api.core.AbstractApiService;
import com.google.api.core.ApiClock;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutureToListenableFuture;
import com.google.api.core.ApiFutures;
import com.google.api.core.ApiService;
import com.google.api.core.CurrentMillisClock;
import com.google.api.core.ForwardingApiFuture;
import com.google.api.core.ListenableFutureToApiFuture;
import com.google.api.core.NanoClock;
import com.google.api.core.SettableApiFuture;
import com.google.api.pathtemplate.PathTemplate;
import com.google.api.pathtemplate.TemplatedResourceName;
import com.google.api.pathtemplate.ValidationException;
import com.google.api.resourcenames.ResourceName;
import com.google.api.resourcenames.ResourceNameFactory;
import com.google.api.resourcenames.UntypedResourceName;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class Api_commonTest {
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Test
    void pathTemplateInstantiatesMatchesAndParsesNamedVariables() {
        PathTemplate template = PathTemplate.create("projects/{project}/locations/{location}/instances/{instance}");
        Map<String, String> bindings = new LinkedHashMap<>();
        bindings.put("project", "project-1");
        bindings.put("location", "us-central1");
        bindings.put("instance", "primary");

        String resourcePath = template.instantiate(bindings);

        assertThat(resourcePath).isEqualTo("projects/project-1/locations/us-central1/instances/primary");
        assertThat(template.vars()).containsExactlyInAnyOrder("project", "location", "instance");
        assertThat(template.matches(resourcePath)).isTrue();
        assertThat(template.match(resourcePath)).containsExactlyEntriesOf(bindings);
        assertThat(template.validatedMatch(resourcePath, "resourceName")).containsExactlyEntriesOf(bindings);
        assertThat(template.singleVar()).isNull();
        assertThat(template.endsWithLiteral()).isFalse();
        assertThat(template.endsWithCustomVerb()).isFalse();
        assertThat(template.parentTemplate().toString())
                .isEqualTo("projects/{project=*}/locations/{location=*}/instances");

        TemplatedResourceName name = template.parse(resourcePath);
        assertThat(name.template()).isEqualTo(template);
        assertThat(name).containsExactlyEntriesOf(bindings);
        assertThat(name.toString()).isEqualTo(resourcePath);
        assertThat(name.parentName().toString()).isEqualTo("projects/project-1/locations/us-central1/instances");
    }

    @Test
    void pathTemplateSupportsPartialInstantiationSubTemplatesAndUrlRoundTrips() {
        PathTemplate template = PathTemplate.create("publishers/{publisher}/books/{book}/chapters/{chapter=**}");

        assertThat(template.instantiate("publisher", "acme", "book", "java", "chapter", "part one/intro"))
                .isEqualTo("publishers/acme/books/java/chapters/part+one/intro");
        assertThat(template.instantiatePartial(Map.of("publisher", "acme")))
                .isEqualTo("publishers/acme/books/{book=*/}/chapters/{chapter=**}");
        assertThat(template.withoutVars().toString()).isEqualTo("publishers/*/books/*/chapters/**");
        assertThatThrownBy(() -> template.subTemplate("missing"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("missing");

        PathTemplate positionalTemplate = PathTemplate.create("encoded/{$0}/items/{$1=**}");
        String encoded = positionalTemplate.encode("space value", "folder/item with space");

        assertThat(encoded).isEqualTo("encoded/space+value/items/folder/item+with+space");
        assertThat(positionalTemplate.decode(encoded)).containsExactly("space value", "folder/item with space");
    }

    @Test
    void pathTemplateCanDisableUrlEncoding() {
        PathTemplate template = PathTemplate.createWithoutUrlEncoding("folders/{folder}/items/{item}");
        Map<String, String> bindings = new LinkedHashMap<>();
        bindings.put("folder", "team alpha");
        bindings.put("item", "raw+value");

        String resourcePath = template.instantiate(bindings);

        assertThat(resourcePath).isEqualTo("folders/team alpha/items/raw+value");
        assertThat(template.matches(resourcePath)).isTrue();
        assertThat(template.match(resourcePath)).containsExactlyEntriesOf(bindings);
        assertThat(template.instantiatePartial(Map.of("folder", "team alpha")))
                .isEqualTo("folders/team alpha/items/{item=*}");
        assertThatThrownBy(() -> template.instantiate(Map.of("folder", "team/alpha", "item", "raw+value")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid character \"/\"");
    }

    @Test
    void pathTemplateSupportsCustomVerbSuffixes() {
        PathTemplate template = PathTemplate.create("projects/{project}/operations/{operation}:cancel");
        Map<String, String> bindings = new LinkedHashMap<>();
        bindings.put("project", "project one");
        bindings.put("operation", "operation-123");

        String resourcePath = template.instantiate(bindings);

        assertThat(resourcePath).isEqualTo("projects/project+one/operations/operation-123:cancel");
        assertThat(template.endsWithCustomVerb()).isTrue();
        assertThat(template.endsWithLiteral()).isFalse();
        assertThat(template.matches(resourcePath)).isTrue();
        assertThat(template.match(resourcePath)).containsExactlyEntriesOf(bindings);
        assertThat(template.match("projects/project+one/operations/operation-123:delete")).isNull();
        assertThat(template.parentTemplate().toString()).isEqualTo("projects/{project=*}/operations/{operation=*}");
    }

    @Test
    void pathTemplateReportsValidationErrorsForInvalidNames() {
        PathTemplate template = PathTemplate.create("projects/{project}/locations/{location}");

        assertThat(template.match("projects/project-1/zones/us-central1-a")).isNull();
        assertThatThrownBy(() -> template.validate("projects/project-1/zones/us-central1-a", "parent"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("parent");
        assertThatThrownBy(() -> template.instantiate(Map.of("project", "project-1")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("location");
        assertThatThrownBy(() -> template.decode("projects/project-1/locations/us-central1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("named bindings");
    }

    @Test
    void templatedResourceNameIsImmutableMapWithEndpointAndPrefixOperations() {
        PathTemplate template = PathTemplate.create("organizations/{organization}/folders/{folder}/projects/{project}");
        TemplatedResourceName name = TemplatedResourceName.create(
                template,
                Map.of("organization", "org-1", "folder", "folder-2", "project", "project-3"));
        TemplatedResourceName sameName = TemplatedResourceName.create(template, name.toString());
        TemplatedResourceName withEndpoint = name.withEndpoint("service.googleapis.com");

        assertThat(name).isEqualTo(sameName).hasSameHashCodeAs(sameName);
        assertThat(name.size()).isEqualTo(3);
        assertThat(name).containsEntry("organization", "org-1");
        assertThat(name.keySet()).containsExactlyInAnyOrder("organization", "folder", "project");
        assertThat(name.values()).containsExactlyInAnyOrder("org-1", "folder-2", "project-3");
        assertThat(name.parentName().toString()).isEqualTo("organizations/org-1/folders/folder-2/projects");
        assertThat(name.startsWith(name.parentName())).isTrue();
        assertThat(name.hasEndpoint()).isFalse();
        assertThat(withEndpoint.hasEndpoint()).isTrue();
        assertThat(withEndpoint.endpoint()).isEqualTo("service.googleapis.com");
        assertThat(withEndpoint).isNotEqualTo(name);
        assertThatThrownBy(() -> name.keySet().add("replacement"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void templatedResourceNameCanMatchFullNamesWithHosts() {
        PathTemplate template = PathTemplate.create("projects/{project}/topics/{topic}");
        TemplatedResourceName fullName = TemplatedResourceName.createFromFullName(
                template,
                "pubsub.googleapis.com/projects/project-1/topics/topic-2");

        assertThat(fullName).isNotNull();
        assertThat(fullName).containsEntry(PathTemplate.HOSTNAME_VAR, "pubsub.googleapis.com");
        assertThat(fullName).containsEntry("project", "project-1");
        assertThat(fullName).containsEntry("topic", "topic-2");
        assertThat(template.matchFromFullName("not-a-matching-name")).isNull();
        assertThat(TemplatedResourceName.createFromFullName(template, "not-a-matching-name")).isNull();
    }

    @Test
    void untypedResourceNamePreservesRawValueAndExposesDefaultField() {
        UntypedResourceName name = UntypedResourceName.parse("projects/project-1/locations/global/keyRings/ring-1");
        ResourceNameFactory<UntypedResourceName> factory = UntypedResourceName::parse;
        ResourceName copied = UntypedResourceName.of(name);

        assertThat(UntypedResourceName.isParsableFrom(name.toString())).isTrue();
        assertThat(UntypedResourceName.isParsableFrom(null)).isFalse();
        assertThat(factory.parse(name.toString())).isEqualTo(name).hasSameHashCodeAs(name);
        assertThat(copied.toString()).isEqualTo(name.toString());
        assertThat(name.getFieldValue("ignored-field-name")).isEqualTo(name.toString());
        assertThat(name.getFieldValuesMap()).containsExactly(Map.entry("", name.toString()));
        assertThatThrownBy(() -> name.getFieldValuesMap().put("another", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void apiFuturesTransformCatchAndCombineResults() throws Exception {
        ApiFuture<Integer> transformed = ApiFutures.transform(
                ApiFutures.immediateFuture("abc"), String::length, DIRECT_EXECUTOR);
        ApiFuture<Integer> recovered = ApiFutures.catching(
                ApiFutures.<Integer>immediateFailedFuture(new IOException("network unavailable")),
                IOException.class,
                exception -> exception.getMessage().length(),
                DIRECT_EXECUTOR);
        ApiFuture<String> recoveredAsync = ApiFutures.catchingAsync(
                ApiFutures.<String>immediateFailedFuture(new IllegalStateException("retryable")),
                IllegalStateException.class,
                exception -> ApiFutures.immediateFuture(exception.getMessage().toUpperCase()),
                DIRECT_EXECUTOR);
        ApiFuture<String> transformedAsync = ApiFutures.transformAsync(
                ApiFutures.immediateFuture("value"),
                value -> ApiFutures.immediateFuture(value + "-async"),
                DIRECT_EXECUTOR);

        assertThat(transformed.get(1, TimeUnit.SECONDS)).isEqualTo(3);
        assertThat(recovered.get(1, TimeUnit.SECONDS)).isEqualTo("network unavailable".length());
        assertThat(recoveredAsync.get(1, TimeUnit.SECONDS)).isEqualTo("RETRYABLE");
        assertThat(transformedAsync.get(1, TimeUnit.SECONDS)).isEqualTo("value-async");
        assertThat(ApiFutures.allAsList(Arrays.asList(transformed, recovered)).get(1, TimeUnit.SECONDS))
                .containsExactly(3, "network unavailable".length());
        assertThat(ApiFutures.successfulAsList(Arrays.asList(
                        transformed,
                        ApiFutures.<Integer>immediateFailedFuture(new IOException("ignored"))))
                .get(1, TimeUnit.SECONDS))
                .containsExactly(3, null);
    }

    @Test
    void settableApiFutureRunsCallbacksListenersAndCancellation() throws Exception {
        SettableApiFuture<String> future = SettableApiFuture.create();
        CountDownLatch listenerLatch = new CountDownLatch(1);
        AtomicReference<String> callbackValue = new AtomicReference<>();
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();

        future.addListener(listenerLatch::countDown, DIRECT_EXECUTOR);
        ApiFutures.addCallback(future, new ApiFutureCallback<>() {
            @Override
            public void onFailure(Throwable throwable) {
                callbackFailure.set(throwable);
            }

            @Override
            public void onSuccess(String value) {
                callbackValue.set(value);
            }
        }, DIRECT_EXECUTOR);

        assertThat(future.set("done")).isTrue();
        assertThat(future.set("again")).isFalse();
        assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo("done");
        assertThat(listenerLatch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(callbackValue).hasValue("done");
        assertThat(callbackFailure.get()).isNull();

        SettableApiFuture<String> cancelled = SettableApiFuture.create();
        assertThat(cancelled.cancel(true)).isTrue();
        assertThat(cancelled.isCancelled()).isTrue();
        assertThat(cancelled.isDone()).isTrue();
        assertThat(ApiFutures.immediateCancelledFuture().isCancelled()).isTrue();
    }

    @Test
    void futureAdaptersBridgeApiFutureAndListenableFuture() throws Exception {
        SettableApiFuture<String> apiFuture = SettableApiFuture.create();
        ForwardingApiFuture<String> forwardingApiFuture = new ForwardingApiFuture<>(apiFuture);
        ListenableFuture<String> listenableFuture = new ApiFutureToListenableFuture<>(forwardingApiFuture);
        CountDownLatch apiToListenableLatch = new CountDownLatch(1);

        listenableFuture.addListener(apiToListenableLatch::countDown, DIRECT_EXECUTOR);
        assertThat(apiFuture.set("api-result")).isTrue();
        assertThat(listenableFuture.get(1, TimeUnit.SECONDS)).isEqualTo("api-result");
        assertThat(apiToListenableLatch.await(1, TimeUnit.SECONDS)).isTrue();

        SettableFuture<String> guavaFuture = SettableFuture.create();
        ApiFuture<String> bridgedApiFuture = new ListenableFutureToApiFuture<>(guavaFuture);
        CountDownLatch listenableToApiLatch = new CountDownLatch(1);
        bridgedApiFuture.addListener(listenableToApiLatch::countDown, DIRECT_EXECUTOR);
        assertThat(guavaFuture.set("listenable-result")).isTrue();

        assertThat(bridgedApiFuture.get(1, TimeUnit.SECONDS)).isEqualTo("listenable-result");
        assertThat(listenableToApiLatch.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void apiFutureFailurePropagatesThroughGetAndCallbacks() throws Exception {
        SettableApiFuture<String> failed = SettableApiFuture.create();
        CountDownLatch callbackLatch = new CountDownLatch(1);
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        IOException failure = new IOException("boom");

        ApiFutures.addCallback(failed, new ApiFutureCallback<>() {
            @Override
            public void onFailure(Throwable throwable) {
                callbackFailure.set(throwable);
                callbackLatch.countDown();
            }

            @Override
            public void onSuccess(String value) {
                throw new AssertionError("failure future must not invoke success callback");
            }
        }, DIRECT_EXECUTOR);
        assertThat(failed.setException(failure)).isTrue();

        assertThatThrownBy(() -> failed.get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCause(failure);
        assertThat(callbackLatch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(callbackFailure).hasValue(failure);
    }

    @Test
    void abstractApiServicePublishesLifecycleTransitions() throws Exception {
        RecordingService service = new RecordingService();
        List<String> events = new CopyOnWriteArrayList<>();
        service.addListener(new ApiService.Listener() {
            @Override
            public void starting() {
                events.add("starting");
            }

            @Override
            public void running() {
                events.add("running");
            }

            @Override
            public void stopping(ApiService.State from) {
                events.add("stopping:" + from);
            }

            @Override
            public void terminated(ApiService.State from) {
                events.add("terminated:" + from);
            }
        }, DIRECT_EXECUTOR);

        assertThat(service.state()).isEqualTo(ApiService.State.NEW);
        assertThat(service.startAsync()).isSameAs(service);
        service.awaitRunning(1, TimeUnit.SECONDS);
        assertThat(service.isRunning()).isTrue();
        assertThat(service.startCalls).isEqualTo(1);

        assertThat(service.stopAsync()).isSameAs(service);
        service.awaitTerminated(1, TimeUnit.SECONDS);

        assertThat(service.state()).isEqualTo(ApiService.State.TERMINATED);
        assertThat(service.stopCalls).isEqualTo(1);
        assertThat(events).containsExactly("starting", "running", "stopping:RUNNING", "terminated:STOPPING");
    }

    @Test
    void abstractApiServiceExposesFailureCause() {
        FailingService service = new FailingService(new IllegalStateException("startup failed"));
        AtomicReference<Throwable> listenerFailure = new AtomicReference<>();
        service.addListener(new ApiService.Listener() {
            @Override
            public void failed(ApiService.State from, Throwable failure) {
                listenerFailure.set(failure);
            }
        }, DIRECT_EXECUTOR);

        service.startAsync();

        assertThat(service.state()).isEqualTo(ApiService.State.FAILED);
        assertThat(service.failureCause()).isInstanceOf(IllegalStateException.class).hasMessage("startup failed");
        assertThat(listenerFailure).hasValue(service.failureCause());
        assertThatThrownBy(() -> service.awaitRunning(1, TimeUnit.SECONDS))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(service.failureCause());
    }

    @Test
    void clocksExposeMonotonicAndWallClockTime() {
        ApiClock nanoClock = NanoClock.getDefaultClock();
        ApiClock currentMillisClock = CurrentMillisClock.getDefaultClock();

        long firstNanoTime = nanoClock.nanoTime();
        long secondNanoTime = nanoClock.nanoTime();
        long wallClockMillis = currentMillisClock.millisTime();
        long convertedNanos = currentMillisClock.nanoTime();

        assertThat(secondNanoTime).isGreaterThanOrEqualTo(firstNanoTime);
        assertThat(nanoClock.millisTime()).isNotNegative();
        assertThat(wallClockMillis).isPositive();
        assertThat(convertedNanos).isPositive();
        assertThat(convertedNanos % TimeUnit.NANOSECONDS.convert(1, TimeUnit.MILLISECONDS)).isZero();
    }

    private static final class RecordingService extends AbstractApiService {
        private int startCalls;
        private int stopCalls;

        @Override
        protected void doStart() {
            startCalls++;
            notifyStarted();
        }

        @Override
        protected void doStop() {
            stopCalls++;
            notifyStopped();
        }
    }

    private static final class FailingService extends AbstractApiService {
        private final RuntimeException failure;

        private FailingService(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        protected void doStart() {
            notifyFailed(failure);
        }

        @Override
        protected void doStop() {
            notifyStopped();
        }
    }
}
