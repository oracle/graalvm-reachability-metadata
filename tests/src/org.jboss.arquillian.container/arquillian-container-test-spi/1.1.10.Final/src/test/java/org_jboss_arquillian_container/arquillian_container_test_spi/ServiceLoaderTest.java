/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_container.arquillian_container_test_spi;

import java.util.Set;

import org.jboss.arquillian.container.test.spi.util.ServiceLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServiceLoaderTest {
    @Test
    void loadsProvidersDeclaredInServiceResource() {
        ServiceLoader<ExampleService> serviceLoader = ServiceLoader.load(
                ExampleService.class,
                ServiceLoaderTest.class.getClassLoader());

        Set<ExampleService> providers = serviceLoader.getProviders();

        assertThat(providers).hasSize(1);

        ExampleService provider = providers.iterator().next();
        assertThat(provider).isInstanceOf(ExampleServiceProvider.class);
        assertThat(provider.name()).isEqualTo("loaded from service resource");
    }

    public interface ExampleService {
        String name();
    }

    public static final class ExampleServiceProvider implements ExampleService {
        public ExampleServiceProvider() {
        }

        @Override
        public String name() {
            return "loaded from service resource";
        }
    }
}
