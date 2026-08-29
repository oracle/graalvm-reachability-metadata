/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.persister.internal.PersisterClassResolverInitiator;
import org.hibernate.persister.internal.StandardPersisterClassResolver;
import org.hibernate.persister.spi.PersisterClassResolver;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class PersisterClassResolverInitiatorTest {

    @Test
    public void instantiatesAConfiguredPersisterClassResolver() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder().build();
        try {
            PersisterClassResolver resolver = PersisterClassResolverInitiator.INSTANCE.initiateService(
                    Map.of(PersisterClassResolverInitiator.IMPL_NAME, StandardPersisterClassResolver.class),
                    (ServiceRegistryImplementor) registry
            );

            assertThat(resolver).isExactlyInstanceOf(StandardPersisterClassResolver.class);
        }
        finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
