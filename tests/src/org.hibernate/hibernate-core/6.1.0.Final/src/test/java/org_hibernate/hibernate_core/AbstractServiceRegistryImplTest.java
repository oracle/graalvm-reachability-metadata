/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.boot.registry.StandardServiceInitiator;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.service.Service;
import org.hibernate.service.spi.InjectService;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractServiceRegistryImplTest {

    @Test
    public void injectsADeclaredServiceDependency() {
        DependencyService dependency = new DependencyService();
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .addService(DependencyService.class, dependency)
                .addInitiator(new InjectedServiceInitiator())
                .build();
        try {
            InjectedService service = registry.getService(InjectedService.class);

            assertThat(service.getDependency()).isSameAs(dependency);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    public static class DependencyService implements Service {
        private static final long serialVersionUID = 1L;
    }

    public static class InjectedServiceInitiator implements StandardServiceInitiator<InjectedService> {
        @Override
        public InjectedService initiateService(
                Map<String, Object> configurationValues,
                ServiceRegistryImplementor registry) {
            return new InjectedService();
        }

        @Override
        public Class<InjectedService> getServiceInitiated() {
            return InjectedService.class;
        }
    }

    public static class InjectedService implements Service {
        private static final long serialVersionUID = 1L;
        private DependencyService dependency;

        @InjectService
        public void injectDependency(DependencyService dependency) {
            this.dependency = dependency;
        }

        public DependencyService getDependency() {
            return dependency;
        }
    }
}
