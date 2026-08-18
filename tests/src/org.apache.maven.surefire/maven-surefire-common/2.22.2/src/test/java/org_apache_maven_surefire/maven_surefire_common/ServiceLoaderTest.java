/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.maven_surefire_common;

import java.util.Set;

import org.apache.maven.surefire.providerapi.ServiceLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServiceLoaderTest {
    @Test
    void loadsImplementationsDeclaredInServiceProviderResource() {
        ServiceLoader serviceLoader = new ServiceLoader();
        ClassLoader classLoader = ServiceLoaderTest.class.getClassLoader();

        Set<GreetingService> services = serviceLoader.load(GreetingService.class, classLoader);

        assertThat(services).hasSize(1);
        assertThat(services.iterator().next().message()).isEqualTo("loaded by SPI");
    }

    public interface GreetingService {
        String message();
    }

    public static final class GreetingServiceProvider implements GreetingService {
        @Override
        public String message() {
            return "loaded by SPI";
        }
    }
}
