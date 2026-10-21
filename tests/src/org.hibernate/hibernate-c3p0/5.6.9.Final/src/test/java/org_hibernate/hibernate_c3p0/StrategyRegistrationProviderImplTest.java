/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_c3p0;

import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.selector.spi.StrategySelector;
import org.hibernate.c3p0.internal.C3P0ConnectionProvider;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

public class StrategyRegistrationProviderImplTest {

    @Test
    @Timeout(60)
    public void resolvesConnectionProviderByC3p0StrategyName() {
        BootstrapServiceRegistry registry = new BootstrapServiceRegistryBuilder().build();
        try {
            StrategySelector strategySelector = registry.getService(StrategySelector.class);

            ConnectionProvider provider = strategySelector.resolveStrategy(ConnectionProvider.class, "c3p0");

            assertThat(provider).isInstanceOf(C3P0ConnectionProvider.class);
        } finally {
            BootstrapServiceRegistryBuilder.destroy(registry);
        }
    }
}
