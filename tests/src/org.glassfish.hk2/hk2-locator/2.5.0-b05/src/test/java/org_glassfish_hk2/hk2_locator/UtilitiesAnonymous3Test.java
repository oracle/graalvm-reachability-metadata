/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_locator;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;

import org.junit.jupiter.api.Test;
import org.jvnet.hk2.internal.Collector;
import org.jvnet.hk2.internal.ServiceLocatorImpl;
import org.jvnet.hk2.internal.Utilities;

public class UtilitiesAnonymous3Test {
    @Test
    void findProducerConstructorInspectsDeclaredConstructors() {
        final ServiceLocatorImpl locator = new ServiceLocatorImpl("utilities-anonymous-3-test", null);
        try {
            final Collector collector = new Collector();

            final Constructor<?> constructor = Utilities.findProducerConstructor(
                    ServiceWithDeclaredConstructors.class,
                    locator,
                    collector);

            assertThat(collector.hasErrors()).isFalse();
            assertThat(constructor).isNotNull();
            assertThat(constructor.getParameterCount()).isZero();
            assertThat(constructor.getDeclaringClass()).isEqualTo(ServiceWithDeclaredConstructors.class);
        } finally {
            locator.shutdown();
        }
    }

    public static final class ServiceWithDeclaredConstructors {
        public ServiceWithDeclaredConstructors() {
        }

        private ServiceWithDeclaredConstructors(String name) {
        }
    }
}
