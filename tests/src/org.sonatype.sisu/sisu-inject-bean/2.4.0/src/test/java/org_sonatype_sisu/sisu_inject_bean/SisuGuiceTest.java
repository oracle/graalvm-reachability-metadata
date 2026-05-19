/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_bean;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.util.Collections;

import javax.inject.Provider;

import org.junit.jupiter.api.Test;
import org.eclipse.sisu.BeanEntry;
import org.eclipse.sisu.Mediator;
import org.eclipse.sisu.inject.BeanLocator;
import org.sonatype.guice.bean.containers.SisuGuice;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;

public class SisuGuiceTest {
    @Test
    @SuppressWarnings("deprecation")
    void enhanceCreatesInjectorProxyThatUsesBeanLocatorLookups() {
        LookupService locatedService = new LookupService();
        Injector injector = Guice.createInjector(binder -> binder.bind(BeanLocator.class)
            .toInstance(new SingleBeanLocator(locatedService)));

        Injector enhancedInjector = SisuGuice.enhance(injector);

        assertThat(enhancedInjector).isNotSameAs(injector);
        assertThat(enhancedInjector.getInstance(LookupService.class)).isSameAs(locatedService);
        assertThat(enhancedInjector.getInstance(Key.get(LookupService.class))).isSameAs(locatedService);
        assertThat(enhancedInjector.getInstance(UnmatchedService.class)).isNull();
    }

    private static final class SingleBeanLocator implements BeanLocator {
        private final LookupService locatedService;

        private SingleBeanLocator(LookupService locatedService) {
            this.locatedService = locatedService;
        }

        @Override
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public <Q extends Annotation, T> Iterable<BeanEntry<Q, T>> locate(Key<T> key) {
            if (LookupService.class.equals(key.getTypeLiteral().getRawType())) {
                return Collections.singletonList((BeanEntry) new SimpleBeanEntry<>(locatedService));
            }
            return Collections.emptyList();
        }

        @Override
        public <Q extends Annotation, T, W> void watch(Key<T> key, Mediator<Q, T, W> mediator, W watcher) {
            throw new UnsupportedOperationException("Watching is not needed for lookup-only test");
        }
    }

    private static final class SimpleBeanEntry<T> implements BeanEntry<Annotation, T> {
        private final T value;

        private SimpleBeanEntry(T value) {
            this.value = value;
        }

        @Override
        public Annotation getKey() {
            return null;
        }

        @Override
        public T getValue() {
            return value;
        }

        @Override
        public T setValue(T value) {
            throw new UnsupportedOperationException("Bean entries are immutable");
        }

        @Override
        public Provider<T> getProvider() {
            return () -> value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Class<T> getImplementationClass() {
            return (Class<T>) value.getClass();
        }

        @Override
        public String getDescription() {
            return "test bean entry";
        }

        @Override
        public Object getSource() {
            return SisuGuiceTest.class;
        }

        @Override
        public int getRank() {
            return 0;
        }
    }

    private static final class LookupService {
    }

    private static final class UnmatchedService {
    }
}
