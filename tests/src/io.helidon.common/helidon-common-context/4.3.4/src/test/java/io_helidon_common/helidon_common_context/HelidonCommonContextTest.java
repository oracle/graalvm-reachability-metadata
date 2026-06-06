/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_common.helidon_common_context;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.context.Context;
import io.helidon.common.context.ContextAwareExecutorService;
import io.helidon.common.context.Contexts;
import io.helidon.common.context.ExecutorException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HelidonCommonContextTest {
    private static final String CLASSIFIER_ONE = "classifier-one";
    private static final String CLASSIFIER_TWO = "classifier-two";

    @Test
    void registersAndRetrievesMostRecentAssignableInstances() {
        Context context = Context.builder().id("registry-context").build();
        Dog firstDog = new Dog("first");
        Dog replacementDog = new Dog("replacement");
        Cat cat = new Cat("cat");

        context.register(firstDog);
        assertThat(context.get(Dog.class).orElseThrow()).isSameAs(firstDog);
        assertThat(context.get(Animal.class).orElseThrow()).isSameAs(firstDog);
        assertThat(context.get(String.class)).isEmpty();

        context.register(replacementDog);
        assertThat(context.get(Dog.class).orElseThrow()).isSameAs(replacementDog);
        assertThat(context.get(Animal.class).orElseThrow()).isSameAs(replacementDog);

        context.register(cat);
        assertThat(context.get(Cat.class).orElseThrow()).isSameAs(cat);
        assertThat(context.get(Animal.class).orElseThrow()).isSameAs(cat);

        context.unregister(cat);
        assertThat(context.get(Cat.class)).isEmpty();
        assertThat(context.get(Animal.class).orElseThrow()).isSameAs(replacementDog);
    }

    @Test
    void lazilySuppliesValuesOnceAndAllowsReplacement() {
        Context context = Context.builder().id("supplier-context").build();
        AtomicInteger supplierCalls = new AtomicInteger();
        Service suppliedService = new Service("supplied");
        Service registeredService = new Service("registered");

        context.supply(Service.class, () -> {
            supplierCalls.incrementAndGet();
            return suppliedService;
        });

        assertThat(supplierCalls).hasValue(0);
        assertThat(context.get(Service.class).orElseThrow()).isSameAs(suppliedService);
        assertThat(context.get(Service.class).orElseThrow()).isSameAs(suppliedService);
        assertThat(supplierCalls).hasValue(1);

        context.register(registeredService);
        assertThat(context.get(Service.class).orElseThrow()).isSameAs(registeredService);

        context.unregister(registeredService);
        assertThat(context.get(Service.class)).isEmpty();
    }

    @Test
    void supportsClassifiedRegistrationsSuppliersAndParentLookup() {
        Context parent = Context.builder().id("classified-parent").build();
        Context child = Context.create(parent);
        AtomicInteger classifiedSupplierCalls = new AtomicInteger();
        ClassifiedValue parentValue = new ClassifiedValue("parent");
        ClassifiedValue suppliedValue = new ClassifiedValue("supplied");
        ClassifiedValue localValue = new ClassifiedValue("local");

        parent.register(CLASSIFIER_ONE, parentValue);
        child.supply(CLASSIFIER_TWO, ClassifiedValue.class, () -> {
            classifiedSupplierCalls.incrementAndGet();
            return suppliedValue;
        });

        assertThat(child.get(CLASSIFIER_ONE, ClassifiedValue.class).orElseThrow()).isSameAs(parentValue);
        assertThat(child.get(CLASSIFIER_TWO, ClassifiedValue.class).orElseThrow()).isSameAs(suppliedValue);
        assertThat(child.get(CLASSIFIER_TWO, ClassifiedValue.class).orElseThrow()).isSameAs(suppliedValue);
        assertThat(classifiedSupplierCalls).hasValue(1);
        assertThat(child.get("missing", ClassifiedValue.class)).isEmpty();

        child.register(CLASSIFIER_ONE, localValue);
        assertThat(child.get(CLASSIFIER_ONE, ClassifiedValue.class).orElseThrow()).isSameAs(localValue);

        child.unregister(CLASSIFIER_ONE, localValue);
        assertThat(child.get(CLASSIFIER_ONE, ClassifiedValue.class).orElseThrow()).isSameAs(parentValue);
    }

    @Test
    void classifiedLookupUsesEqualClassifierKeys() {
        Context context = Context.builder().id("classifier-equality-context").build();
        ClassifierKey registrationKey = new ClassifierKey("tenant");
        ClassifierKey lookupKey = new ClassifierKey("tenant");
        ClassifierKey otherKey = new ClassifierKey("other");
        ClassifiedValue value = new ClassifiedValue("classified-by-equality");

        context.register(registrationKey, value);

        assertThat(lookupKey).isNotSameAs(registrationKey).isEqualTo(registrationKey);
        assertThat(context.get(lookupKey, ClassifiedValue.class).orElseThrow()).isSameAs(value);
        assertThat(context.get(otherKey, ClassifiedValue.class)).isEmpty();
    }

    @Test
    void validatesNullInputsOnPublicRegistryMethods() {
        Context context = Context.builder().id("null-validation-context").build();

        assertThatThrownBy(() -> context.register(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> context.unregister(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> context.supply(null, Service::new)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> context.supply(Service.class, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> context.get(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> context.register(null, new Service())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> context.unregister(null, new Service())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> context.supply(null, Service.class, Service::new))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> context.supply(CLASSIFIER_ONE, null, Service::new))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> context.supply(CLASSIFIER_ONE, Service.class, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> context.get(null, Service.class)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> context.get(CLASSIFIER_ONE, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void builderUsesCustomIdGlobalParentAndHierarchicalChildIds() {
        GlobalOnly globalValue = new GlobalOnly("global");
        Context globalContext = Contexts.globalContext();
        Context customParent = Context.builder().id("custom-parent").build();
        Context child = Context.create(customParent);
        Context customChild = Context.builder().parent(customParent).id("custom-child").build();

        globalContext.register(globalValue);
        try {
            assertThat(globalContext.id()).isEqualTo("helidon");
            assertThat(customParent.id()).isEqualTo("custom-parent");
            assertThat(child.id()).startsWith("custom-parent:");
            assertThat(customChild.id()).isEqualTo("custom-child");
            assertThat(customParent.get(GlobalOnly.class).orElseThrow()).isSameAs(globalValue);
        } finally {
            globalContext.unregister(globalValue);
        }
    }

    @Test
    void runInContextMaintainsNestedThreadLocalStackAndRestoresAfterCompletion() {
        Context outer = Context.builder().id("outer").build();
        Context inner = Context.builder().id("inner").build();
        AtomicReference<Optional<Context>> before = new AtomicReference<>();
        AtomicReference<Context> seenOuter = new AtomicReference<>();
        AtomicReference<Context> seenInner = new AtomicReference<>();
        AtomicReference<Context> restoredOuter = new AtomicReference<>();
        AtomicReference<Optional<Context>> after = new AtomicReference<>();

        before.set(Contexts.context());
        Contexts.runInContext(outer, () -> {
            seenOuter.set(Contexts.context().orElseThrow());
            Contexts.runInContext(inner, () -> seenInner.set(Contexts.context().orElseThrow()));
            restoredOuter.set(Contexts.context().orElseThrow());
        });
        after.set(Contexts.context());

        assertThat(before.get()).isEmpty();
        assertThat(seenOuter.get()).isSameAs(outer);
        assertThat(seenInner.get()).isSameAs(inner);
        assertThat(restoredOuter.get()).isSameAs(outer);
        assertThat(after.get()).isEmpty();
    }

    @Test
    void callableContextHelpersReturnValuesAndHandleExceptions() throws Exception {
        Context context = Context.builder().id("callable-context").build();

        String value = Contexts.runInContext(context, () -> Contexts.context().orElseThrow().id());
        assertThat(value).isEqualTo("callable-context");

        IOException ioException = new IOException("checked");
        assertThatExceptionOfType(ExecutorException.class)
                .isThrownBy(() -> Contexts.runInContext(context, () -> {
                    throw ioException;
                }))
                .withCause(ioException);

        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> Contexts.runInContextWithThrow(context, () -> {
                    throw ioException;
                }))
                .satisfies(thrown -> assertThat(thrown).isSameAs(ioException));

        String checkedResult = Contexts.runInContextWithThrow(context, () -> "checked-result");
        assertThat(checkedResult).isEqualTo("checked-result");
    }

    @Test
    void wrappedExecutorCapturesContextWhenTaskIsSubmitted() throws Exception {
        ExecutorService delegate = Executors.newSingleThreadExecutor();
        ExecutorService wrapped = Contexts.wrap(delegate);
        Context context = Context.builder().id("executor-context").build();
        Context otherContext = Context.builder().id("other-context").build();

        try {
            assertThat(wrapped).isInstanceOf(ContextAwareExecutorService.class);
            assertThat(((ContextAwareExecutorService) wrapped).unwrap()).isSameAs(delegate);
            assertThat(Contexts.wrap(wrapped)).isSameAs(wrapped);

            Future<String> future = Contexts.runInContext(context,
                    () -> wrapped.submit(() -> Contexts.context().orElseThrow().id()));
            assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("executor-context");

            Contexts.runInContext(otherContext,
                    () -> assertThat(Contexts.context().orElseThrow()).isSameAs(otherContext));
            assertThat(Contexts.context()).isEmpty();
        } finally {
            wrapped.shutdownNow();
            assertThat(wrapped.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void wrappedExecutorPropagatesContextForExecuteInvokeAllAndInvokeAny() throws Exception {
        ExecutorService delegate = Executors.newFixedThreadPool(2);
        ExecutorService wrapped = Contexts.wrap(delegate);
        Context context = Context.builder().id("bulk-executor-context").build();
        CountDownLatch executeLatch = new CountDownLatch(1);
        AtomicReference<String> executeContextId = new AtomicReference<>();

        try {
            Contexts.runInContext(context, () -> wrapped.execute(() -> {
                executeContextId.set(Contexts.context().orElseThrow().id());
                executeLatch.countDown();
            }));
            assertThat(executeLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(executeContextId.get()).isEqualTo("bulk-executor-context");

            List<Callable<String>> tasks = List.of(
                    () -> Contexts.context().orElseThrow().id(),
                    () -> Contexts.context().orElseThrow().id() + "-second");
            List<Future<String>> futures = Contexts.runInContext(context, () -> wrapped.invokeAll(tasks));
            assertThat(futures.get(0).get(5, TimeUnit.SECONDS)).isEqualTo("bulk-executor-context");
            assertThat(futures.get(1).get(5, TimeUnit.SECONDS)).isEqualTo("bulk-executor-context-second");

            String invokeAnyResult = Contexts.runInContext(context,
                    () -> wrapped.invokeAny(List.of(() -> Contexts.context().orElseThrow().id())));
            assertThat(invokeAnyResult).isEqualTo("bulk-executor-context");
        } finally {
            wrapped.shutdownNow();
            assertThat(wrapped.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void wrappedScheduledExecutorCapturesContextForDelayedTasks() throws Exception {
        ScheduledExecutorService delegate = Executors.newSingleThreadScheduledExecutor();
        ScheduledExecutorService wrapped = Contexts.wrap(delegate);
        Context context = Context.builder().id("scheduled-context").build();

        try {
            assertThat(Contexts.wrap(wrapped)).isSameAs(wrapped);

            ScheduledFuture<String> callableFuture = Contexts.runInContext(context,
                    () -> wrapped.schedule(() -> Contexts.context().orElseThrow().id(), 0, TimeUnit.MILLISECONDS));
            assertThat(callableFuture.get(5, TimeUnit.SECONDS)).isEqualTo("scheduled-context");

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> runnableContextId = new AtomicReference<>();
            Contexts.runInContext(context, () -> wrapped.schedule(() -> {
                runnableContextId.set(Contexts.context().orElseThrow().id());
                latch.countDown();
            }, 0, TimeUnit.MILLISECONDS));

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(runnableContextId.get()).isEqualTo("scheduled-context");
        } finally {
            wrapped.shutdownNow();
            assertThat(wrapped.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void wrappedScheduledExecutorCapturesContextForPeriodicTasks() throws Exception {
        ScheduledExecutorService delegate = Executors.newSingleThreadScheduledExecutor();
        ScheduledExecutorService wrapped = Contexts.wrap(delegate);
        Context fixedRateContext = Context.builder().id("fixed-rate-context").build();
        Context fixedDelayContext = Context.builder().id("fixed-delay-context").build();
        CountDownLatch fixedRateLatch = new CountDownLatch(2);
        CountDownLatch fixedDelayLatch = new CountDownLatch(2);
        AtomicReference<String> fixedRateContextId = new AtomicReference<>();
        AtomicReference<String> fixedDelayContextId = new AtomicReference<>();
        AtomicInteger fixedRateRuns = new AtomicInteger();
        AtomicInteger fixedDelayRuns = new AtomicInteger();

        try {
            ScheduledFuture<?> fixedRateFuture = Contexts.runInContext(fixedRateContext,
                    () -> wrapped.scheduleAtFixedRate(() -> {
                        fixedRateContextId.set(Contexts.context().orElseThrow().id());
                        fixedRateRuns.incrementAndGet();
                        fixedRateLatch.countDown();
                    }, 0, 10, TimeUnit.MILLISECONDS));
            assertThat(fixedRateLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(fixedRateFuture.cancel(true)).isTrue();

            ScheduledFuture<?> fixedDelayFuture = Contexts.runInContext(fixedDelayContext,
                    () -> wrapped.scheduleWithFixedDelay(() -> {
                        fixedDelayContextId.set(Contexts.context().orElseThrow().id());
                        fixedDelayRuns.incrementAndGet();
                        fixedDelayLatch.countDown();
                    }, 0, 10, TimeUnit.MILLISECONDS));
            assertThat(fixedDelayLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(fixedDelayFuture.cancel(true)).isTrue();

            assertThat(fixedRateContextId.get()).isEqualTo("fixed-rate-context");
            assertThat(fixedDelayContextId.get()).isEqualTo("fixed-delay-context");
            assertThat(fixedRateRuns.get()).isGreaterThanOrEqualTo(2);
            assertThat(fixedDelayRuns.get()).isGreaterThanOrEqualTo(2);
            assertThat(Contexts.context()).isEmpty();
        } finally {
            wrapped.shutdownNow();
            assertThat(wrapped.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private interface Animal {
        String name();
    }

    private static final class Dog implements Animal {
        private final String name;

        private Dog(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }
    }

    private static final class Cat implements Animal {
        private final String name;

        private Cat(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }
    }

    private static final class Service {
        private final String name;

        private Service() {
            this("default");
        }

        private Service(String name) {
            this.name = name;
        }

        private String name() {
            return name;
        }
    }

    private record ClassifierKey(String value) {
    }

    private static final class ClassifiedValue {
        private final String value;

        private ClassifiedValue(String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }
    }

    private static final class GlobalOnly {
        private final String value;

        private GlobalOnly(String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }
    }
}
