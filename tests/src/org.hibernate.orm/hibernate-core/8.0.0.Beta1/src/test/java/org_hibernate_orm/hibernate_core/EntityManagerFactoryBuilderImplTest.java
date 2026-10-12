/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.hibernate.jpa.boot.spi.JpaSettings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EntityManagerFactoryBuilderImplTest {

    private static final AtomicInteger INTEGRATIONS = new AtomicInteger();

    @Test
    void integratorProviderNamedInSettingsContributesIntegrators() {
        INTEGRATIONS.set(0);
        Map<String, Object> properties = new HashMap<>();
        properties.put("jakarta.persistence.jdbc.url", SessionFactoryTestSupport.newH2Url());
        properties.put(JpaSettings.INTEGRATOR_PROVIDER, CountingIntegratorProvider.class.getName());

        EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("StudentPU", properties);
        try {
            assertThat(entityManagerFactory.getMetamodel().getEntities()).isNotEmpty();
        } finally {
            entityManagerFactory.close();
        }
        assertThat(INTEGRATIONS).hasValue(1);
    }

    public static class CountingIntegratorProvider implements IntegratorProvider {

        @Override
        public List<Integrator> getIntegrators() {
            return List.of(new Integrator() {
                @Override
                public void integrate(
                        Metadata metadata,
                        BootstrapContext bootstrapContext,
                        SessionFactoryImplementor sessionFactory) {
                    INTEGRATIONS.incrementAndGet();
                }
            });
        }
    }
}
