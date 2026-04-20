/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_context;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class Opentelemetry_contextTest {
    private static final ContextKey<String> TENANT_KEY = ContextKey.named("tenant");
    private static final ContextKey<String> REQUEST_KEY = ContextKey.named("request-id");

    private static final TextMapSetter<Map<String, String>> MAP_SETTER = Map::put;
    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier.get(key);
        }
    };

    @Test
    void contextsStoreValuesImmutablyAndScopesRestorePreviousContext() {
        Context rootContext = Context.root();
        Context tenantContext = rootContext.with(TENANT_KEY, "tenant-a");
        Context requestContext = tenantContext.with(REQUEST_KEY, "request-1");

        assertThat(rootContext.get(TENANT_KEY)).isNull();
        assertThat(rootContext.get(REQUEST_KEY)).isNull();
        assertThat(tenantContext.get(TENANT_KEY)).isEqualTo("tenant-a");
        assertThat(tenantContext.get(REQUEST_KEY)).isNull();
        assertThat(requestContext.get(TENANT_KEY)).isEqualTo("tenant-a");
        assertThat(requestContext.get(REQUEST_KEY)).isEqualTo("request-1");

        assertThat(Context.current().get(TENANT_KEY)).isNull();
        assertThat(Context.current().get(REQUEST_KEY)).isNull();

        try (Scope tenantScope = tenantContext.makeCurrent()) {
            assertThat(Context.current().get(TENANT_KEY)).isEqualTo("tenant-a");
            assertThat(Context.current().get(REQUEST_KEY)).isNull();

            try (Scope requestScope = requestContext.makeCurrent()) {
                assertThat(Context.current().get(TENANT_KEY)).isEqualTo("tenant-a");
                assertThat(Context.current().get(REQUEST_KEY)).isEqualTo("request-1");
            }

            assertThat(Context.current().get(TENANT_KEY)).isEqualTo("tenant-a");
            assertThat(Context.current().get(REQUEST_KEY)).isNull();
        }

        assertThat(Context.current().get(TENANT_KEY)).isNull();
        assertThat(Context.current().get(REQUEST_KEY)).isNull();
    }

    @Test
    void implicitContextKeyedValuesCanBeStoredAndMadeCurrent() {
        Context baseContext = Context.root().with(TENANT_KEY, "tenant-b");
        TestImplicitContextValue implicitValue = new TestImplicitContextValue("session-7");

        Context storedContext = baseContext.with(implicitValue);
        assertThat(baseContext.get(TestImplicitContextValue.KEY)).isNull();
        assertThat(storedContext.get(TestImplicitContextValue.KEY)).isSameAs(implicitValue);
        assertThat(storedContext.get(TENANT_KEY)).isEqualTo("tenant-b");

        try (Scope scope = baseContext.makeCurrent()) {
            try (Scope implicitScope = implicitValue.makeCurrent()) {
                assertThat(Context.current().get(TENANT_KEY)).isEqualTo("tenant-b");
                assertThat(Context.current().get(TestImplicitContextValue.KEY)).isSameAs(implicitValue);
            }

            assertThat(Context.current().get(TENANT_KEY)).isEqualTo("tenant-b");
            assertThat(Context.current().get(TestImplicitContextValue.KEY)).isNull();
        }
    }

    @Test
    void wrappedFunctionalInterfacesUseCapturedContext() throws Exception {
        TestImplicitContextValue implicitValue = new TestImplicitContextValue("session-9");
        Context capturedContext = Context.root()
                .with(TENANT_KEY, "tenant-c")
                .with(REQUEST_KEY, "request-9")
                .with(implicitValue);

        AtomicReference<String> runnableTenant = new AtomicReference<>();
        AtomicReference<TestImplicitContextValue> runnableImplicit = new AtomicReference<>();
        Runnable wrappedRunnable = capturedContext.wrap(() -> {
            runnableTenant.set(Context.current().get(TENANT_KEY));
            runnableImplicit.set(Context.current().get(TestImplicitContextValue.KEY));
        });
        Callable<String> wrappedCallable = capturedContext.wrap(
                () -> Context.current().get(TENANT_KEY) + ":" + Context.current().get(REQUEST_KEY)
        );
        Supplier<String> wrappedSupplier = capturedContext.wrapSupplier(() -> Context.current().get(REQUEST_KEY));
        Function<String, String> wrappedFunction = capturedContext.wrapFunction(
                value -> value + "-" + Context.current().get(TENANT_KEY)
        );
        BiFunction<String, String, String> wrappedBiFunction = capturedContext.wrapFunction(
                (left, right) -> left + right + "-" + Context.current().get(REQUEST_KEY)
        );
        List<String> consumedValues = new ArrayList<>();
        Consumer<String> wrappedConsumer = capturedContext.wrapConsumer(
                value -> consumedValues.add(value + ":" + Context.current().get(TENANT_KEY))
        );
        Map<String, String> consumedEntries = new LinkedHashMap<>();
        BiConsumer<String, String> wrappedBiConsumer = capturedContext.wrapConsumer(
                (key, value) -> consumedEntries.put(key, value + ":" + Context.current().get(REQUEST_KEY))
        );

        try (Scope scope = Context.root().with(TENANT_KEY, "outer-tenant").with(REQUEST_KEY, "outer-request").makeCurrent()) {
            wrappedRunnable.run();
            assertThat(runnableTenant.get()).isEqualTo("tenant-c");
            assertThat(runnableImplicit.get()).isSameAs(implicitValue);
            assertThat(wrappedCallable.call()).isEqualTo("tenant-c:request-9");
            assertThat(wrappedSupplier.get()).isEqualTo("request-9");
            assertThat(wrappedFunction.apply("work")).isEqualTo("work-tenant-c");
            assertThat(wrappedBiFunction.apply("left", "right")).isEqualTo("leftright-request-9");

            wrappedConsumer.accept("value");
            wrappedBiConsumer.accept("key", "value");

            assertThat(Context.current().get(TENANT_KEY)).isEqualTo("outer-tenant");
            assertThat(Context.current().get(REQUEST_KEY)).isEqualTo("outer-request");
        }

        assertThat(consumedValues).containsExactly("value:tenant-c");
        assertThat(consumedEntries).containsEntry("key", "value:request-9");
    }

    @Test
    void executorWrappersPropagateExplicitAndCurrentContext() throws Exception {
        Context explicitContext = Context.root().with(TENANT_KEY, "tenant-d");

        Deque<Runnable> queuedTasks = new ArrayDeque<>();
        Executor queuedExecutor = command -> queuedTasks.addLast(command);

        Executor wrappedExecutor = explicitContext.wrap(queuedExecutor);
        AtomicReference<String> explicitExecutorTenant = new AtomicReference<>();
        wrappedExecutor.execute(() -> explicitExecutorTenant.set(Context.current().get(TENANT_KEY)));
        assertThat(explicitExecutorTenant.get()).isNull();
        queuedTasks.removeFirst().run();
        assertThat(explicitExecutorTenant.get()).isEqualTo("tenant-d");

        Executor currentContextExecutor = Context.taskWrapping(queuedExecutor);
        AtomicReference<String> currentExecutorTenant = new AtomicReference<>();
        try (Scope scope = Context.root().with(TENANT_KEY, "tenant-e").makeCurrent()) {
            currentContextExecutor.execute(() -> currentExecutorTenant.set(Context.current().get(TENANT_KEY)));
        }
        queuedTasks.removeFirst().run();
        assertThat(currentExecutorTenant.get()).isEqualTo("tenant-e");

        try (ExecutorService delegate = Executors.newSingleThreadExecutor()) {
            ExecutorService wrappedService = explicitContext.wrap(delegate);
            Future<String> wrappedResult = wrappedService.submit(() -> Context.current().get(TENANT_KEY));
            assertThat(wrappedResult.get(5, TimeUnit.SECONDS)).isEqualTo("tenant-d");
        }

        try (ExecutorService delegate = Executors.newSingleThreadExecutor()) {
            ExecutorService currentContextService = Context.taskWrapping(delegate);
            Future<String> currentContextResult;
            try (Scope scope = Context.root().with(TENANT_KEY, "tenant-f").makeCurrent()) {
                currentContextResult = currentContextService.submit(() -> Context.current().get(TENANT_KEY));
            }
            assertThat(currentContextResult.get(5, TimeUnit.SECONDS)).isEqualTo("tenant-f");
        }
    }

    @Test
    void scheduledExecutorServiceUsesCapturedContext() throws Exception {
        Context scheduledContext = Context.root().with(TENANT_KEY, "tenant-g");

        try (ScheduledExecutorService delegate = Executors.newSingleThreadScheduledExecutor()) {
            ScheduledExecutorService wrappedScheduler = scheduledContext.wrap(delegate);
            Future<String> scheduledResult = wrappedScheduler.schedule(
                    () -> Context.current().get(TENANT_KEY),
                    0,
                    TimeUnit.MILLISECONDS
            );
            assertThat(scheduledResult.get(5, TimeUnit.SECONDS)).isEqualTo("tenant-g");
        }
    }

    @Test
    void textMapPropagatorsInjectExtractAndCompose() {
        TextMapPropagator tenantPropagator = new MapCarrierPropagator("tenant", TENANT_KEY);
        TextMapPropagator requestPropagator = new MapCarrierPropagator("request-id", REQUEST_KEY);
        TextMapPropagator compositePropagator = TextMapPropagator.composite(tenantPropagator, requestPropagator);

        Context sourceContext = Context.root().with(TENANT_KEY, "tenant-h").with(REQUEST_KEY, "request-11");
        Map<String, String> carrier = new LinkedHashMap<>();
        compositePropagator.inject(sourceContext, carrier, MAP_SETTER);

        assertThat(compositePropagator.fields()).containsExactly("tenant", "request-id");
        assertThat(carrier).containsEntry("tenant", "tenant-h");
        assertThat(carrier).containsEntry("request-id", "request-11");

        Context extractedContext = compositePropagator.extract(Context.root(), carrier, MAP_GETTER);
        assertThat(extractedContext.get(TENANT_KEY)).isEqualTo("tenant-h");
        assertThat(extractedContext.get(REQUEST_KEY)).isEqualTo("request-11");

        TextMapPropagator noopPropagator = TextMapPropagator.noop();
        Map<String, String> noopCarrier = new LinkedHashMap<>();
        noopPropagator.inject(sourceContext, noopCarrier, MAP_SETTER);
        assertThat(noopCarrier).isEmpty();
        assertThat(noopPropagator.fields()).isEmpty();
        assertThat(noopPropagator.extract(null, carrier, MAP_GETTER).get(TENANT_KEY)).isNull();

        ContextPropagators propagators = ContextPropagators.create(compositePropagator);
        assertThat(propagators.getTextMapPropagator()).isSameAs(compositePropagator);
        assertThat(ContextPropagators.noop().getTextMapPropagator().fields()).isEmpty();
        assertThat(TextMapPropagator.composite(List.of()).fields()).isEmpty();
    }

    private static final class TestImplicitContextValue implements ImplicitContextKeyed {
        private static final ContextKey<TestImplicitContextValue> KEY = ContextKey.named("implicit-value");

        private final String value;

        private TestImplicitContextValue(String value) {
            this.value = value;
        }

        @Override
        public Context storeInContext(Context context) {
            return context.with(KEY, this);
        }

        @Override
        public String toString() {
            return value;
        }
    }

    private static final class MapCarrierPropagator implements TextMapPropagator {
        private final String fieldName;
        private final ContextKey<String> contextKey;

        private MapCarrierPropagator(String fieldName, ContextKey<String> contextKey) {
            this.fieldName = fieldName;
            this.contextKey = contextKey;
        }

        @Override
        public List<String> fields() {
            return List.of(fieldName);
        }

        @Override
        public <C> void inject(Context context, C carrier, TextMapSetter<C> setter) {
            if (context == null || carrier == null || setter == null) {
                return;
            }
            String value = context.get(contextKey);
            if (value != null) {
                setter.set(carrier, fieldName, value);
            }
        }

        @Override
        public <C> Context extract(Context context, C carrier, TextMapGetter<C> getter) {
            Context baseContext = context == null ? Context.root() : context;
            if (carrier == null || getter == null) {
                return baseContext;
            }
            String value = getter.get(carrier, fieldName);
            return value == null ? baseContext : baseContext.with(contextKey, value);
        }
    }
}
