/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_core.arquillian_core_impl_base;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.arquillian.core.api.Event;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.ApplicationScoped;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.core.impl.ExtensionImpl;
import org.jboss.arquillian.core.spi.Extension;
import org.junit.jupiter.api.Test;

public class ReflectionsTest {
    @Test
    void extensionFactoryDiscoversInjectedFieldsEventFieldsAndObserverMethods() {
        Extension extension = ExtensionImpl.of(new ExtensionFixture());

        assertThat(extension.getInjectionPoints()).hasSize(4);
        assertThat(extension.getEventPoints()).hasSize(2);
        assertThat(extension.getObservers()).hasSize(2);
    }

    @SuppressWarnings("unused")
    private static class BaseExtensionFixture {
        @Inject
        private Instance<String> inheritedInstance;

        @Inject
        @ApplicationScoped
        private InstanceProducer<Integer> inheritedProducer;

        @Inject
        private Event<String> inheritedEvent;

        private void observeInherited(@Observes String event) {
        }
    }

    @SuppressWarnings("unused")
    private static final class ExtensionFixture extends BaseExtensionFixture {
        @Inject
        private Instance<Long> instance;

        @Inject
        @ApplicationScoped
        private InstanceProducer<Double> producer;

        @Inject
        private Event<Long> event;

        private void observe(@Observes Long event) {
        }
    }
}
