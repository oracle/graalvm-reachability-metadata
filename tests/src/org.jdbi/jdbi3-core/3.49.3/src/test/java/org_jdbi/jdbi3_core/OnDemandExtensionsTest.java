/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi.jdbi3_core;

import java.util.Set;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.extension.ExtensionFactory;
import org.jdbi.v3.core.extension.Extensions;
import org.jdbi.v3.core.extension.HandleSupplier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jdbi.v3.core.extension.ExtensionFactory.FactoryFlag.DONT_USE_PROXY;

public class OnDemandExtensionsTest {
    @Test
    void forwardsOnDemandCallsToAConcreteExtension() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:on_demand_extensions;DB_CLOSE_DELAY=-1");
        jdbi.getConfig(Extensions.class).register(new DirectFactory());

        DirectExtension extension = jdbi.onDemand(DirectExtension.class);

        assertThat(extension.message("native")).isEqualTo("direct-native");
        assertThat(extension.toString()).contains(DirectExtension.class.getName());
    }

    public interface DirectExtension {
        String message(String value);
    }

    public static final class DirectImplementation implements DirectExtension {
        @Override
        public String message(String value) {
            return "direct-" + value;
        }
    }

    public static final class DirectFactory implements ExtensionFactory {
        @Override
        public boolean accepts(Class<?> extensionType) {
            return extensionType == DirectExtension.class;
        }

        @Override
        public <E> E attach(Class<E> extensionType, HandleSupplier handleSupplier) {
            return extensionType.cast(new DirectImplementation());
        }

        @Override
        public Set<FactoryFlag> getFactoryFlags() {
            return Set.of(DONT_USE_PROXY);
        }
    }
}
