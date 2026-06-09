/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.sisu.inject.BindingPublisher;
import org.eclipse.sisu.inject.BindingSubscriber;
import org.eclipse.sisu.inject.InjectorBindings;
import org.junit.jupiter.api.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

public class InjectorBindingsTest {
    @Test
    void findBindingPublisherCreatesPublisherFromParentTemplateForChildInjector() throws Exception {
        registerGuicePrimitiveParserMethods();
        Injector parent = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(BindingPublisher.class).to(TemplatePublisher.class);
            }
        });
        Injector child = parent.createChildInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ChildMarker.class).toInstance(new ChildMarker());
            }
        });

        BindingPublisher publisher = InjectorBindings.findBindingPublisher(child);

        assertThat(publisher).isInstanceOf(TemplatePublisher.class);
        assertThat(((TemplatePublisher) publisher).injector()).isSameAs(child);
        assertThat(publisher.adapt(Injector.class)).isSameAs(child);
    }

    private static void registerGuicePrimitiveParserMethods() throws NoSuchMethodException {
        Integer.class.getMethod("parseInt", String.class);
        Long.class.getMethod("parseLong", String.class);
        Boolean.class.getMethod("parseBoolean", String.class);
        Byte.class.getMethod("parseByte", String.class);
        Short.class.getMethod("parseShort", String.class);
        Float.class.getMethod("parseFloat", String.class);
        Double.class.getMethod("parseDouble", String.class);
    }

    private static final class ChildMarker {
    }

    public static final class TemplatePublisher implements BindingPublisher {
        private final Injector injector;

        @Inject
        public TemplatePublisher(Injector injector) {
            this.injector = injector;
        }

        @Override
        public <T> void subscribe(BindingSubscriber<T> subscriber) {
        }

        @Override
        public <T> void unsubscribe(BindingSubscriber<T> subscriber) {
        }

        @Override
        public int maxBindingRank() {
            return 0;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T adapt(Class<T> type) {
            return Injector.class == type ? (T) injector : null;
        }

        private Injector injector() {
            return injector;
        }
    }
}
