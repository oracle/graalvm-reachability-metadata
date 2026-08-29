/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class EntityManagerFactoryBuilderImplTest {

    @Test
    public void instantiatesTheConfiguredIntegratorProvider() {
        RecordingIntegratorProvider.instances.set(0);
        Map<String, Object> properties = new HashMap<>();
        properties.put(
                EntityManagerFactoryBuilderImpl.INTEGRATOR_PROVIDER,
                RecordingIntegratorProvider.class.getName()
        );
        properties.put(AvailableSettings.URL, "jdbc:h2:mem:integrator-provider");
        properties.put(AvailableSettings.DRIVER, "org.h2.Driver");
        properties.put(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect");
        properties.put(AvailableSettings.JAKARTA_VALIDATION_MODE, "none");

        EntityManagerFactory factory =
                Persistence.createEntityManagerFactory("StudentPU", properties);
        try {
            assertThat(factory.isOpen()).isTrue();
            assertThat(RecordingIntegratorProvider.instances).hasValue(1);
        }
        finally {
            factory.close();
        }
    }

    public static class RecordingIntegratorProvider implements IntegratorProvider {
        private static final AtomicInteger instances = new AtomicInteger();

        public RecordingIntegratorProvider() {
            instances.incrementAndGet();
        }

        @Override
        public List<Integrator> getIntegrators() {
            return List.of();
        }
    }
}
