/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.sisu.inject.BindingSubscriber;
import org.eclipse.sisu.inject.InjectorPublisher;
import org.eclipse.sisu.inject.RankingFunction;
import org.junit.jupiter.api.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.MembersInjector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.TypeConverterBinding;

public class InjectorPublisherTest {
    @Test
    void subscribePublishesBindingDeclaredThroughGuiceElementSource() {
        Binding<Service> binding = serviceBindingFromModule();
        RecordingSubscriber<Service> subscriber = new RecordingSubscriber<>(TypeLiteral.get(Service.class));
        InjectorPublisher publisher = new InjectorPublisher(new SingleBindingInjector<>(binding), fixedRanking(19));

        publisher.subscribe(subscriber);

        assertThat(subscriber.addedBindings()).containsExactly(binding);
        assertThat(subscriber.ranks()).containsExactly(19);
    }

    @SuppressWarnings("unchecked")
    private static Binding<Service> serviceBindingFromModule() {
        List<Element> elements = Elements.getElements(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Service.class).to(ServiceImpl.class);
            }
        });

        return elements.stream()
            .filter(Binding.class::isInstance)
            .map(element -> (Binding<?>) element)
            .filter(binding -> Key.get(Service.class).equals(binding.getKey()))
            .map(binding -> (Binding<Service>) binding)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Expected a Guice binding for Service"));
    }

    private static RankingFunction fixedRanking(int rank) {
        return new RankingFunction() {
            @Override
            public int maxRank() {
                return rank;
            }

            @Override
            public <T> int rank(Binding<T> binding) {
                return rank;
            }
        };
    }

    private interface Service {
    }

    private static final class ServiceImpl implements Service {
    }

    private static final class RecordingSubscriber<T> implements BindingSubscriber<T> {
        private final TypeLiteral<T> type;
        private final List<Binding<T>> addedBindings = new ArrayList<>();
        private final List<Integer> ranks = new ArrayList<>();

        private RecordingSubscriber(TypeLiteral<T> type) {
            this.type = type;
        }

        @Override
        public TypeLiteral<T> type() {
            return type;
        }

        @Override
        public void add(Binding<T> binding, int rank) {
            addedBindings.add(binding);
            ranks.add(rank);
        }

        @Override
        public void remove(Binding<T> binding) {
            addedBindings.remove(binding);
        }

        @Override
        public Iterable<Binding<T>> bindings() {
            return addedBindings;
        }

        private List<Binding<T>> addedBindings() {
            return addedBindings;
        }

        private List<Integer> ranks() {
            return ranks;
        }
    }

    private static final class SingleBindingInjector<T> implements Injector {
        private final Binding<T> binding;
        private final Map<Key<?>, Binding<?>> bindings;

        private SingleBindingInjector(Binding<T> binding) {
            this.binding = binding;
            this.bindings = Collections.singletonMap(binding.getKey(), binding);
        }

        @Override
        public void injectMembers(Object instance) {
            throw new UnsupportedOperationException("Member injection is not required for publishing bindings");
        }

        @Override
        public <S> MembersInjector<S> getMembersInjector(TypeLiteral<S> typeLiteral) {
            throw new UnsupportedOperationException("Member injection is not required for publishing bindings");
        }

        @Override
        public <S> MembersInjector<S> getMembersInjector(Class<S> type) {
            throw new UnsupportedOperationException("Member injection is not required for publishing bindings");
        }

        @Override
        public Map<Key<?>, Binding<?>> getBindings() {
            return bindings;
        }

        @Override
        public Map<Key<?>, Binding<?>> getAllBindings() {
            return bindings;
        }

        @Override
        public <S> Binding<S> getBinding(Key<S> key) {
            throw new UnsupportedOperationException("Direct binding lookup is not required for publishing bindings");
        }

        @Override
        public <S> Binding<S> getBinding(Class<S> type) {
            throw new UnsupportedOperationException("Direct binding lookup is not required for publishing bindings");
        }

        @Override
        public <S> Binding<S> getExistingBinding(Key<S> key) {
            throw new UnsupportedOperationException("Direct binding lookup is not required for publishing bindings");
        }

        @Override
        @SuppressWarnings("unchecked")
        public <S> List<Binding<S>> findBindingsByType(TypeLiteral<S> type) {
            if (binding.getKey().getTypeLiteral().equals(type)) {
                return Collections.singletonList((Binding<S>) binding);
            }
            return Collections.emptyList();
        }

        @Override
        public <S> Provider<S> getProvider(Key<S> key) {
            throw new UnsupportedOperationException("Provider lookup is not required for publishing bindings");
        }

        @Override
        public <S> Provider<S> getProvider(Class<S> type) {
            throw new UnsupportedOperationException("Provider lookup is not required for publishing bindings");
        }

        @Override
        public <S> S getInstance(Key<S> key) {
            throw new UnsupportedOperationException("Instance lookup is not required for publishing bindings");
        }

        @Override
        public <S> S getInstance(Class<S> type) {
            throw new UnsupportedOperationException("Instance lookup is not required for publishing bindings");
        }

        @Override
        public Injector getParent() {
            return null;
        }

        @Override
        public Injector createChildInjector(Iterable<? extends Module> modules) {
            throw new UnsupportedOperationException("Child injectors are not required for publishing bindings");
        }

        @Override
        public Injector createChildInjector(Module... modules) {
            throw new UnsupportedOperationException("Child injectors are not required for publishing bindings");
        }

        @Override
        public Map<Class<? extends Annotation>, Scope> getScopeBindings() {
            return Collections.emptyMap();
        }

        @Override
        public Set<TypeConverterBinding> getTypeConverterBindings() {
            return Collections.emptySet();
        }

        @Override
        public List<Element> getElements() {
            return Collections.emptyList();
        }

        @Override
        public Map<TypeLiteral<?>, List<InjectionPoint>> getAllMembersInjectorInjectionPoints() {
            return Collections.emptyMap();
        }
    }
}
