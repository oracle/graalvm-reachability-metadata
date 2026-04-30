/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_core.arquillian_core_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;

import org.jboss.arquillian.core.api.Event;
import org.jboss.arquillian.core.api.Injector;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.ApplicationScoped;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.core.api.annotation.Scope;
import org.jboss.arquillian.core.api.event.ManagerStarted;
import org.jboss.arquillian.core.api.event.ManagerStopping;
import org.jboss.arquillian.core.api.threading.ContextSnapshot;
import org.jboss.arquillian.core.api.threading.ExecutorService;
import org.junit.jupiter.api.Test;

public class Arquillian_core_apiTest {
    @Test
    public void lifecycleEventsCanBePublishedAsStronglyTypedPayloads() {
        RecordingEvent<Object> eventBus = new RecordingEvent<>();
        ManagerStarted started = new ManagerStarted();
        ManagerStopping stopping = new ManagerStopping();

        eventBus.fire(started);
        eventBus.fire("between-lifecycle-events");
        eventBus.fire(stopping);

        assertThat(eventBus.events()).containsExactly(started, "between-lifecycle-events", stopping);
        assertThat(started).isNotSameAs(new ManagerStarted());
        assertThat(stopping).isNotSameAs(new ManagerStopping());
    }

    @Test
    public void instanceProducerCanBeConsumedThroughReadOnlyInstanceView() {
        SimpleInstanceProducer<CharSequence> producer = new SimpleInstanceProducer<>();
        Instance<CharSequence> readOnlyView = producer;
        StringBuilder mutableValue = new StringBuilder("mutable");

        producer.set("initial");
        assertThat(readOnlyView.get()).isEqualTo("initial");

        producer.set(mutableValue);
        mutableValue.append(" value");
        assertThat(readOnlyView.get()).hasToString("mutable value");

        producer.set(null);
        assertThat(readOnlyView.get()).isNull();
    }

    @Test
    public void injectorCanWireAnnotatedExtensionUsingPublicContracts() {
        SimpleInstanceProducer<String> messageProducer = new SimpleInstanceProducer<>();
        SimpleInstanceProducer<String> applicationStateProducer = new SimpleInstanceProducer<>();
        SimpleInjector injector = new SimpleInjector(messageProducer, applicationStateProducer);
        AnnotatedExtension extension = new AnnotatedExtension();

        AnnotatedExtension injectedExtension = injector.inject(extension);
        messageProducer.set("core-ready");
        applicationStateProducer.set("application-scope");

        assertThat(injectedExtension).isSameAs(extension);
        assertThat(extension.onManagerStarted(new ManagerStarted())).isEqualTo("core-ready:ManagerStarted");
        assertThat(extension.onManagerStopping(new ManagerStopping()))
                .isEqualTo("application-scope:ManagerStopping");
    }

    @Test
    public void observerPrecedenceCanPrioritizeEventNotifications() {
        PrioritizedEvent<String> eventBus = new PrioritizedEvent<>();
        List<String> notifications = new ArrayList<>();

        eventBus.register(new PrioritizedObservation<>(
                new ObservesLiteral(-10), event -> notifications.add("low:" + event)));
        eventBus.register(new PrioritizedObservation<>(
                new ObservesLiteral(250), event -> notifications.add("high:" + event)));
        eventBus.register(new PrioritizedObservation<>(
                new ObservesLiteral(0), event -> notifications.add("default:" + event)));

        eventBus.fire("manager-started");

        assertThat(notifications)
                .containsExactly("high:manager-started", "default:manager-started", "low:manager-started");
    }

    @Test
    public void executorServiceSnapshotsAndRestoresContextForSubmittedWork() throws Exception {
        ThreadLocal<String> activeContext = new ThreadLocal<>();
        SnapshottingExecutorService executorService = new SnapshottingExecutorService(activeContext);

        activeContext.set("request-one");
        Future<String> submitted = executorService.submit(() -> activeContext.get() + ":handled");

        assertThat(submitted.get()).isEqualTo("request-one:handled");
        assertThat(activeContext.get()).isEqualTo("request-one");

        ContextSnapshot snapshot = executorService.createSnapshotContext();
        activeContext.set("request-two");
        snapshot.activate();
        assertThat(activeContext.get()).isEqualTo("request-one");
        snapshot.deactivate();
        assertThat(activeContext.get()).isEqualTo("request-two");
    }

    @Test
    public void scopeAnnotationsCanSelectIndependentContextualInstances() {
        ScopedInstanceRegistry registry = new ScopedInstanceRegistry();
        ApplicationScoped applicationScope = new ApplicationScopedLiteral();
        CustomScoped customScope = new CustomScopedLiteral();

        registry.producer(applicationScope).set("global-configuration");
        registry.producer(customScope).set("tenant-configuration");

        Instance<String> applicationView = registry.instance(applicationScope);
        Instance<String> customView = registry.instance(customScope);

        assertThat(applicationView.get()).isEqualTo("global-configuration");
        assertThat(customView.get()).isEqualTo("tenant-configuration");

        registry.producer(applicationScope).set("global-refresh");
        assertThat(applicationView.get()).isEqualTo("global-refresh");
        assertThat(customView.get()).isEqualTo("tenant-configuration");
    }

    private static final class RecordingEvent<T> implements Event<T> {
        private final List<T> events = new ArrayList<>();

        @Override
        public void fire(T event) {
            events.add(event);
        }

        private List<T> events() {
            return events;
        }
    }

    private static final class PrioritizedEvent<T> implements Event<T> {
        private final List<PrioritizedObservation<T>> observations = new ArrayList<>();

        private void register(PrioritizedObservation<T> observation) {
            observations.add(observation);
        }

        @Override
        public void fire(T event) {
            observations.stream()
                    .sorted(Comparator.comparingInt(PrioritizedObservation<T>::precedence).reversed())
                    .forEach(observation -> observation.accept(event));
        }
    }

    private static final class PrioritizedObservation<T> {
        private final Observes observes;
        private final Consumer<T> observer;

        private PrioritizedObservation(Observes observes, Consumer<T> observer) {
            this.observes = observes;
            this.observer = observer;
        }

        private int precedence() {
            return observes.precedence();
        }

        private void accept(T event) {
            observer.accept(event);
        }
    }

    private static final class ObservesLiteral implements Observes {
        private final int precedence;

        private ObservesLiteral(int precedence) {
            this.precedence = precedence;
        }

        @Override
        public int precedence() {
            return precedence;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Observes.class;
        }
    }

    private static final class SimpleInstanceProducer<T> implements InstanceProducer<T> {
        private T value;

        @Override
        public void set(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return value;
        }
    }

    private static final class ScopedInstanceRegistry {
        private final Map<Class<? extends Annotation>, SimpleInstanceProducer<String>> instances = new HashMap<>();

        private InstanceProducer<String> producer(Annotation scope) {
            return instances.computeIfAbsent(scope.annotationType(), ignored -> new SimpleInstanceProducer<>());
        }

        private Instance<String> instance(Annotation scope) {
            return producer(scope);
        }
    }

    private static final class SimpleInjector implements Injector {
        private final InstanceProducer<String> messageProducer;
        private final InstanceProducer<String> applicationStateProducer;

        private SimpleInjector(InstanceProducer<String> messageProducer,
                InstanceProducer<String> applicationStateProducer) {
            this.messageProducer = messageProducer;
            this.applicationStateProducer = applicationStateProducer;
        }

        @Override
        public <T> T inject(T target) {
            if (target instanceof AnnotatedExtension) {
                AnnotatedExtension extension = (AnnotatedExtension) target;
                extension.messageProducer = messageProducer;
                extension.applicationStateProducer = applicationStateProducer;
            }
            return target;
        }
    }

    private static final class AnnotatedExtension {
        @Inject
        private Instance<String> messageProducer;

        @Inject
        @ApplicationScoped
        @CustomScoped
        private Instance<String> applicationStateProducer;

        private String onManagerStarted(@Observes(precedence = 100) ManagerStarted event) {
            return messageProducer.get() + ":" + (event == null ? "missing" : "ManagerStarted");
        }

        private String onManagerStopping(@Observes ManagerStopping event) {
            return applicationStateProducer.get() + ":" + (event == null ? "missing" : "ManagerStopping");
        }
    }

    @Scope
    private @interface CustomScoped {
    }

    private static final class ApplicationScopedLiteral implements ApplicationScoped {
        @Override
        public Class<? extends Annotation> annotationType() {
            return ApplicationScoped.class;
        }
    }

    private static final class CustomScopedLiteral implements CustomScoped {
        @Override
        public Class<? extends Annotation> annotationType() {
            return CustomScoped.class;
        }
    }

    private static final class SnapshottingExecutorService implements ExecutorService {
        private final ThreadLocal<String> activeContext;

        private SnapshottingExecutorService(ThreadLocal<String> activeContext) {
            this.activeContext = activeContext;
        }

        @Override
        public <T> Future<T> submit(Callable<T> callable) {
            ContextSnapshot snapshot = createSnapshotContext();
            FutureTask<T> task = new FutureTask<>(() -> {
                snapshot.activate();
                try {
                    return callable.call();
                } finally {
                    snapshot.deactivate();
                }
            });
            task.run();
            return task;
        }

        @Override
        public ContextSnapshot createSnapshotContext() {
            return new ThreadLocalContextSnapshot(activeContext, activeContext.get());
        }
    }

    private static final class ThreadLocalContextSnapshot implements ContextSnapshot {
        private final ThreadLocal<String> activeContext;
        private final String capturedContext;
        private String previousContext;

        private ThreadLocalContextSnapshot(ThreadLocal<String> activeContext, String capturedContext) {
            this.activeContext = activeContext;
            this.capturedContext = capturedContext;
        }

        @Override
        public void activate() {
            previousContext = activeContext.get();
            activeContext.set(capturedContext);
        }

        @Override
        public void deactivate() {
            if (previousContext == null) {
                activeContext.remove();
            } else {
                activeContext.set(previousContext);
            }
        }
    }
}
