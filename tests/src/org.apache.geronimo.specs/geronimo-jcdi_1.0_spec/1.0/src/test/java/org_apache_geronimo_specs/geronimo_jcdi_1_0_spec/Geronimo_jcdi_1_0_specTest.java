/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_jcdi_1_0_spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.BusyConversationException;
import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.NonexistentConversationException;
import javax.enterprise.context.spi.Context;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Event;
import javax.enterprise.inject.AmbiguousResolutionException;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.IllegalProductException;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.New;
import javax.enterprise.inject.ResolutionException;
import javax.enterprise.inject.UnproxyableResolutionException;
import javax.enterprise.inject.UnsatisfiedResolutionException;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.util.AnnotationLiteral;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Qualifier;

import org.junit.jupiter.api.Test;

public class Geronimo_jcdi_1_0_specTest {
    @Test
    void annotationLiteralImplementsQualifierMembersAndEquality() {
        Ranked highPriority = new RankedLiteral(100, "high");
        Ranked sameHighPriority = new RankedLiteral(100, "high");
        Ranked lowPriority = new RankedLiteral(1, "low");

        assertThat(highPriority.annotationType()).isEqualTo(Ranked.class);
        assertThat(highPriority.value()).isEqualTo(100);
        assertThat(highPriority.label()).isEqualTo("high");
        assertThat(highPriority).isEqualTo(sameHighPriority);
        assertThat(highPriority.hashCode()).isEqualTo(sameHighPriority.hashCode());
        assertThat(highPriority).isNotEqualTo(lowPriority);
        assertThat(highPriority.toString()).contains("Ranked");
    }

    @Test
    void builtInQualifierLiteralsExposeAnnotationTypesAndMembers() {
        assertThat(DefaultLiteral.INSTANCE.annotationType()).isEqualTo(Default.class);
        assertThat(AnyLiteral.INSTANCE.annotationType()).isEqualTo(Any.class);

        NewLiteral serviceNew = new NewLiteral(Service.class);
        NewLiteral objectNew = new NewLiteral(Object.class);

        assertThat(serviceNew.annotationType()).isEqualTo(New.class);
        assertThat(serviceNew.value()).isEqualTo(Service.class);
        assertThat(serviceNew).isEqualTo(new NewLiteral(Service.class));
        assertThat(serviceNew).isNotEqualTo(objectNew);
    }

    @Test
    void typeLiteralCapturesNestedGenericTypes() {
        TypeLiteral<Map<String, List<Integer>>> literal = new TypeLiteral<Map<String, List<Integer>>>() {
            private static final long serialVersionUID = 1L;
        };

        assertThat(literal.getType().getTypeName())
                .contains("java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>");
    }

    @Test
    void contextualObjectsAreCreatedCachedAndDestroyedByContext() {
        RecordingContext context = new RecordingContext();
        RecordingCreationalContext<Service> creationalContext = new RecordingCreationalContext<>();
        ServiceContextual contextual = new ServiceContextual("alpha");

        Service created = context.get(contextual, creationalContext);
        Service cached = context.get(contextual);

        assertThat(context.getScope()).isEqualTo(Dependent.class);
        assertThat(context.isActive()).isTrue();
        assertThat(created).isSameAs(cached);
        assertThat(created.name).isEqualTo("alpha");
        assertThat(contextual.createdCount).isEqualTo(1);
        assertThat(creationalContext.incompleteInstances).containsExactly(created);

        contextual.destroy(created, creationalContext);

        assertThat(contextual.destroyedInstances).containsExactly(created);
        assertThat(creationalContext.released).isTrue();
    }

    @Test
    void eventSelectKeepsTypeAndQualifierInformationWhenFiringEvents() {
        RecordingEvent<Number> event = new RecordingEvent<>();

        Event<Integer> integerEvents = event.select(Integer.class, new RankedLiteral(10, "integer"));
        integerEvents.fire(42);
        event.select(new RankedLiteral(20, "number")).fire(3.5D);

        assertThat(event.records).hasSize(2);
        assertThat(event.records.get(0).payload).isEqualTo(42);
        assertThat(event.records.get(0).selectedType).isEqualTo(Integer.class.getName());
        assertThat(event.records.get(0).qualifiers).containsExactly(new RankedLiteral(10, "integer"));
        assertThat(event.records.get(1).payload).isEqualTo(3.5D);
        assertThat(event.records.get(1).selectedType).isEqualTo(Number.class.getName());
        assertThat(event.records.get(1).qualifiers).containsExactly(new RankedLiteral(20, "number"));
    }

    @Test
    void instanceSelectFiltersBySubtypeAndReportsResolutionState() {
        QueueInstance<Number> numbers = new QueueInstance<>(Arrays.asList(1, 2, 3.5D), Collections.emptyList(),
                Number.class.getName());

        Instance<Integer> integers = numbers.select(Integer.class, DefaultLiteral.INSTANCE);
        TypeLiteral<Integer> integerType = new TypeLiteral<Integer>() {
            private static final long serialVersionUID = 1L;
        };
        Instance<Integer> integersFromTypeLiteral = numbers.select(integerType, AnyLiteral.INSTANCE);
        Instance<Number> qualifiedNumbers = numbers.select(new RankedLiteral(5, "all-numbers"));

        assertThat(numbers.isAmbiguous()).isTrue();
        assertThat(numbers.isUnsatisfied()).isFalse();
        assertThat(numbers.get()).isEqualTo(1);
        assertThat(integers).containsExactly(1, 2);
        assertThat(integers.isAmbiguous()).isTrue();
        assertThat(integersFromTypeLiteral).containsExactly(1, 2);
        assertThat(((QueueInstance<Integer>) integersFromTypeLiteral).selectedType).isEqualTo(Integer.class.getName());
        assertThat(((QueueInstance<Number>) qualifiedNumbers).qualifiers)
                .containsExactly(new RankedLiteral(5, "all-numbers"));
    }

    @Test
    void injectionTargetCoordinatesManagedBeanLifecycleCallbacks() {
        Repository repository = new Repository("primary");
        LifecycleInjectionTarget injectionTarget = new LifecycleInjectionTarget(repository);
        RecordingCreationalContext<ManagedComponent> creationalContext = new RecordingCreationalContext<>();

        ManagedComponent component = injectionTarget.produce(creationalContext);
        injectionTarget.inject(component, creationalContext);
        injectionTarget.postConstruct(component);
        injectionTarget.preDestroy(component);
        injectionTarget.dispose(component);

        assertThat(component.name).isEqualTo("managed-component");
        assertThat(component.repository).isSameAs(repository);
        assertThat(component.repository.name).isEqualTo("primary");
        assertThat(component.initialized).isTrue();
        assertThat(component.destroyed).isTrue();
        assertThat(creationalContext.incompleteInstances).containsExactly(component);
        assertThat(injectionTarget.getInjectionPoints()).isEmpty();
        assertThat(injectionTarget.disposedInstances).containsExactly(component);
    }

    @Test
    void cdiExceptionsKeepMessagesAndCauses() {
        IllegalStateException cause = new IllegalStateException("root cause");

        assertExceptionCarriesMessageAndCause(new AmbiguousResolutionException("ambiguous", cause), "ambiguous", cause);
        assertExceptionCarriesMessageAndCause(new BusyConversationException("busy", cause), "busy", cause);
        assertExceptionCarriesMessageAndCause(new ContextNotActiveException("inactive", cause), "inactive", cause);
        assertExceptionCarriesMessageAndCause(new CreationException("creation", cause), "creation", cause);
        assertExceptionCarriesMessageAndCause(new IllegalProductException("illegal product", cause),
                "illegal product", cause);
        assertExceptionCarriesMessageAndCause(new NonexistentConversationException("missing", cause), "missing", cause);
        assertExceptionCarriesMessageAndCause(new ResolutionException("resolution", cause), "resolution", cause);
        assertExceptionCarriesMessageAndCause(new UnsatisfiedResolutionException("unsatisfied", cause),
                "unsatisfied", cause);
        assertExceptionCarriesMessageAndCause(new UnproxyableResolutionException("unproxyable", cause),
                "unproxyable", cause);
    }

    private static void assertExceptionCarriesMessageAndCause(RuntimeException exception, String message,
            Throwable cause) {
        assertThat(exception).hasMessage(message);
        assertThat(exception).hasCause(cause);
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
    private @interface Ranked {
        int value();

        String label();
    }

    private static final class RankedLiteral extends AnnotationLiteral<Ranked> implements Ranked {
        private static final long serialVersionUID = 1L;

        private final int value;
        private final String label;

        private RankedLiteral(int value, String label) {
            this.value = value;
            this.label = label;
        }

        @Override
        public int value() {
            return value;
        }

        @Override
        public String label() {
            return label;
        }
    }

    private static final class DefaultLiteral extends AnnotationLiteral<Default> implements Default {
        private static final long serialVersionUID = 1L;
        private static final DefaultLiteral INSTANCE = new DefaultLiteral();
    }

    private static final class AnyLiteral extends AnnotationLiteral<Any> implements Any {
        private static final long serialVersionUID = 1L;
        private static final AnyLiteral INSTANCE = new AnyLiteral();
    }

    private static final class NewLiteral extends AnnotationLiteral<New> implements New {
        private static final long serialVersionUID = 1L;

        private final Class<?> value;

        private NewLiteral(Class<?> value) {
            this.value = value;
        }

        @Override
        public Class<?> value() {
            return value;
        }
    }

    private static final class Service {
        private final String name;

        private Service(String name) {
            this.name = name;
        }
    }

    private static final class Repository {
        private final String name;

        private Repository(String name) {
            this.name = name;
        }
    }

    private static final class ManagedComponent {
        private final String name;
        private Repository repository;
        private boolean initialized;
        private boolean destroyed;

        private ManagedComponent(String name) {
            this.name = name;
        }
    }

    private static final class LifecycleInjectionTarget implements InjectionTarget<ManagedComponent> {
        private final Repository repository;
        private final List<ManagedComponent> disposedInstances = new ArrayList<>();

        private LifecycleInjectionTarget(Repository repository) {
            this.repository = repository;
        }

        @Override
        public ManagedComponent produce(CreationalContext<ManagedComponent> creationalContext) {
            ManagedComponent component = new ManagedComponent("managed-component");
            creationalContext.push(component);
            return component;
        }

        @Override
        public void inject(ManagedComponent instance, CreationalContext<ManagedComponent> creationalContext) {
            instance.repository = repository;
        }

        @Override
        public void postConstruct(ManagedComponent instance) {
            instance.initialized = true;
        }

        @Override
        public void preDestroy(ManagedComponent instance) {
            instance.destroyed = true;
        }

        @Override
        public void dispose(ManagedComponent instance) {
            disposedInstances.add(instance);
        }

        @Override
        public Set<InjectionPoint> getInjectionPoints() {
            return Collections.emptySet();
        }
    }

    private static final class ServiceContextual implements Contextual<Service> {
        private final String serviceName;
        private final List<Service> destroyedInstances = new ArrayList<>();
        private int createdCount;

        private ServiceContextual(String serviceName) {
            this.serviceName = serviceName;
        }

        @Override
        public Service create(CreationalContext<Service> creationalContext) {
            createdCount++;
            Service service = new Service(serviceName);
            creationalContext.push(service);
            return service;
        }

        @Override
        public void destroy(Service instance, CreationalContext<Service> creationalContext) {
            destroyedInstances.add(instance);
            creationalContext.release();
        }
    }

    private static final class RecordingCreationalContext<T> implements CreationalContext<T> {
        private final List<T> incompleteInstances = new ArrayList<>();
        private boolean released;

        @Override
        public void push(T incompleteInstance) {
            incompleteInstances.add(incompleteInstance);
        }

        @Override
        public void release() {
            released = true;
        }
    }

    private static final class RecordingContext implements Context {
        private final Map<Contextual<?>, Object> instances = new HashMap<>();

        @Override
        public Class<? extends Annotation> getScope() {
            return Dependent.class;
        }

        @Override
        public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
            T existing = get(contextual);
            if (existing != null) {
                return existing;
            }
            T created = contextual.create(creationalContext);
            instances.put(contextual, created);
            return created;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Contextual<T> contextual) {
            return (T) instances.get(contextual);
        }

        @Override
        public boolean isActive() {
            return true;
        }
    }

    private static final class EventRecord {
        private final Object payload;
        private final String selectedType;
        private final List<Annotation> qualifiers;

        private EventRecord(Object payload, String selectedType, List<Annotation> qualifiers) {
            this.payload = payload;
            this.selectedType = selectedType;
            this.qualifiers = qualifiers;
        }
    }

    private static final class RecordingEvent<T> implements Event<T> {
        private final List<EventRecord> records;
        private final List<Annotation> qualifiers;
        private final String selectedType;

        private RecordingEvent() {
            this(new ArrayList<>(), Collections.emptyList(), Number.class.getName());
        }

        private RecordingEvent(List<EventRecord> records, List<Annotation> qualifiers, String selectedType) {
            this.records = records;
            this.qualifiers = qualifiers;
            this.selectedType = selectedType;
        }

        @Override
        public void fire(T event) {
            records.add(new EventRecord(event, selectedType, qualifiers));
        }

        @Override
        public Event<T> select(Annotation... qualifiers) {
            return new RecordingEvent<>(records, mergeQualifiers(qualifiers), selectedType);
        }

        @Override
        public <U extends T> Event<U> select(Class<U> subtype, Annotation... qualifiers) {
            return new RecordingEvent<>(records, mergeQualifiers(qualifiers), subtype.getName());
        }

        @Override
        public <U extends T> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
            return new RecordingEvent<>(records, mergeQualifiers(qualifiers), subtype.getType().getTypeName());
        }

        private List<Annotation> mergeQualifiers(Annotation[] additionalQualifiers) {
            List<Annotation> merged = new ArrayList<>(qualifiers);
            merged.addAll(Arrays.asList(additionalQualifiers));
            return merged;
        }
    }

    private static final class QueueInstance<T> implements Instance<T> {
        private final List<T> values;
        private final List<Annotation> qualifiers;
        private final String selectedType;

        private QueueInstance(Collection<? extends T> values, List<Annotation> qualifiers, String selectedType) {
            this.values = new ArrayList<>(values);
            this.qualifiers = qualifiers;
            this.selectedType = selectedType;
        }

        @Override
        public T get() {
            if (values.isEmpty()) {
                throw new UnsatisfiedResolutionException("No instances are available for " + selectedType);
            }
            return values.get(0);
        }

        @Override
        public Instance<T> select(Annotation... qualifiers) {
            return new QueueInstance<>(values, mergeQualifiers(qualifiers), selectedType);
        }

        @Override
        public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
            List<U> selectedValues = new ArrayList<>();
            for (T value : values) {
                if (subtype.isInstance(value)) {
                    selectedValues.add(subtype.cast(value));
                }
            }
            return new QueueInstance<>(selectedValues, mergeQualifiers(qualifiers), subtype.getName());
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
            String typeName = subtype.getType().getTypeName();
            List<U> selectedValues = new ArrayList<>();
            for (T value : values) {
                if (!Integer.class.getName().equals(typeName) || Integer.class.isInstance(value)) {
                    selectedValues.add((U) value);
                }
            }
            return new QueueInstance<>(selectedValues, mergeQualifiers(qualifiers), typeName);
        }

        @Override
        public boolean isUnsatisfied() {
            return values.isEmpty();
        }

        @Override
        public boolean isAmbiguous() {
            return values.size() > 1;
        }

        @Override
        public Iterator<T> iterator() {
            return values.iterator();
        }

        private List<Annotation> mergeQualifiers(Annotation[] additionalQualifiers) {
            List<Annotation> merged = new ArrayList<>(qualifiers);
            merged.addAll(Arrays.asList(additionalQualifiers));
            return merged;
        }
    }
}
