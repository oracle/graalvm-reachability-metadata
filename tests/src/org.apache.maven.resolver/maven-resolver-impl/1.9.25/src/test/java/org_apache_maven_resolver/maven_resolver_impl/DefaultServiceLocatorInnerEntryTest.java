/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_resolver.maven_resolver_impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.junit.jupiter.api.Test;

public class DefaultServiceLocatorInnerEntryTest {
    @Test
    void createsRegisteredServiceUsingDeclaredNoArgConstructor() {
        DefaultServiceLocator locator = new DefaultServiceLocator();
        locator.setService(DemoService.class, PrivateConstructorDemoService.class);

        DemoService service = locator.getService(DemoService.class);

        assertThat(service).isInstanceOf(PrivateConstructorDemoService.class);
        PrivateConstructorDemoService demoService = (PrivateConstructorDemoService) service;
        assertThat(demoService.marker()).isEqualTo("created");
        assertThat(demoService.initialized).isTrue();
        assertThat(demoService.locator).isSameAs(locator);
        assertThat(locator.getService(DemoService.class)).isSameAs(service);
    }

    private interface DemoService {
        String marker();
    }

    private static final class PrivateConstructorDemoService implements DemoService, Service {
        private final String marker;

        private boolean initialized;

        private ServiceLocator locator;

        private PrivateConstructorDemoService() {
            marker = "created";
        }

        @Override
        public String marker() {
            return marker;
        }

        @Override
        public void initService(ServiceLocator locator) {
            initialized = true;
            this.locator = locator;
        }
    }
}
