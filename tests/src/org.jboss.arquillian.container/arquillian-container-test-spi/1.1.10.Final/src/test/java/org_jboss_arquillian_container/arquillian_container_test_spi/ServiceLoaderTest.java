/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_container.arquillian_container_test_spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.jboss.arquillian.container.test.spi.util.ServiceLoader;
import org.junit.jupiter.api.Test;

public class ServiceLoaderTest {
    @Test
    void loadDiscoversAndInstantiatesProvidersFromServiceResource() {
        ClassLoader classLoader = ServiceLoaderTest.class.getClassLoader();
        ServiceLoader<CharSequence> serviceLoader = ServiceLoader.load(CharSequence.class, classLoader);

        Set<CharSequence> providers = serviceLoader.getProviders();

        assertThat(providers)
                .hasSize(1)
                .first()
                .isExactlyInstanceOf(StringBuilder.class)
                .hasToString("");
    }
}
