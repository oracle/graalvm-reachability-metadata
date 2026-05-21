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

import org.eclipse.sisu.wire.WireModule;
import org.junit.jupiter.api.Test;
import org.sonatype.guice.bean.locators.BeanLocator;
import org.sonatype.inject.BeanEntry;
import org.sonatype.inject.Mediator;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;

public class ElementAnalyzerTest {
    @Test
    void wireModuleAnalyzesLegacyLocatorBindingsWhenCompatibilityApiIsPresent() {
        BeanLocator legacyLocator = new LegacyBeanLocator();
        Module applicationModule = new AbstractModule() {
            @Override
            protected void configure() {
                bind(BeanLocator.class).toInstance(legacyLocator);
            }
        };

        List<Key<?>> recordedBindingKeys = bindingKeys(Elements.getElements(new WireModule(applicationModule)));

        assertThat(BeanLocator.class.getName()).isEqualTo("org.sonatype.guice.bean.locators.BeanLocator");
        assertThat(recordedBindingKeys).contains(Key.get(BeanLocator.class));
    }

    @Test
    void wireModuleRoutesUnresolvedConstructorDependenciesToCustomWiring() {
        Collaborator collaborator = new WiredCollaborator();
        List<Key<?>> wiredKeys = new ArrayList<>();
        Module applicationModule = new AbstractModule() {
            @Override
            protected void configure() {
                bind(NeedsCollaborator.class);
            }
        };

        Module wireModule = new WireModule(applicationModule).with((Binder binder) -> (Key<?> key) -> {
            wiredKeys.add(key);
            if (Key.get(Collaborator.class).equals(key)) {
                binder.bind(Collaborator.class).toInstance(collaborator);
                return true;
            }
            return false;
        });

        List<Key<?>> recordedBindingKeys = bindingKeys(Elements.getElements(wireModule));

        assertThat(wiredKeys).containsExactly(Key.get(Collaborator.class));
        assertThat(recordedBindingKeys).contains(Key.get(NeedsCollaborator.class), Key.get(Collaborator.class));
    }

    private static List<Key<?>> bindingKeys(Iterable<Element> elements) {
        List<Key<?>> keys = new ArrayList<>();
        for (Element element : elements) {
            if (element instanceof Binding<?>) {
                keys.add(((Binding<?>) element).getKey());
            }
        }
        return keys;
    }

    private static final class LegacyBeanLocator implements BeanLocator {
        @Override
        public <Q extends Annotation, T> Iterable<BeanEntry<Q, T>> locate(Key<T> key) {
            return Collections.emptyList();
        }

        @Override
        public <Q extends Annotation, T, W> void watch(Key<T> key, Mediator<Q, T, W> mediator, W watcher) {
        }
    }

    private interface Collaborator {
    }

    private static final class WiredCollaborator implements Collaborator {
    }

    private static final class NeedsCollaborator {
        private final Collaborator collaborator;

        @Inject
        private NeedsCollaborator(Collaborator collaborator) {
            this.collaborator = collaborator;
        }

        private Collaborator collaborator() {
            return collaborator;
        }
    }
}
