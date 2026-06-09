/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;

import javax.inject.Inject;

import org.eclipse.sisu.inject.BindingPublisher;
import org.eclipse.sisu.inject.BindingSubscriber;
import org.eclipse.sisu.inject.InjectorBindings;
import org.junit.jupiter.api.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

public class InjectorBindingsTest {
    @Test
    void findBindingPublisherCreatesChildPublisherFromParentTemplate() {
        Injector parent = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(BindingPublisher.class).to(TemplateBindingPublisher.class);
            }
        });
        Injector child = parent.createChildInjector(new AbstractModule() {
        });

        BindingPublisher publisher = InjectorBindings.findBindingPublisher(child);

        assertThat(publisher).isInstanceOf(TemplateBindingPublisher.class);
        assertThat(((TemplateBindingPublisher) publisher).injector()).isSameAs(child);
    }

    public static final class TemplateBindingPublisher implements BindingPublisher {
        private final Injector injector;

        @Inject
        public TemplateBindingPublisher(Injector injector) {
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
        public <T> T adapt(Class<T> type) {
            return Injector.class == type ? type.cast(injector) : null;
        }

        private Injector injector() {
            return injector;
        }
    }
}
