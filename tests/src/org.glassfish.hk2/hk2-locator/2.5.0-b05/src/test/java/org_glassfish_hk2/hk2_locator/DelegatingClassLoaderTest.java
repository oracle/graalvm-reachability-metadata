/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_locator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.net.URL;

import org.jvnet.hk2.internal.ProxyUtilities;
import org.jvnet.hk2.internal.ServiceLocatorImpl;
import org.junit.jupiter.api.Test;

public class DelegatingClassLoaderTest {
    @Test
    void proxyClassLoaderDelegatesClassAndResourceLookups() throws Exception {
        final ServiceLocatorImpl locator = new ServiceLocatorImpl("delegating-class-loader-test", null);
        try {
            final GreetingService service = new ProxyUtilities().generateProxy(
                    GreetingService.class,
                    locator,
                    null,
                    null,
                    null);

            assertThat(GreetingService.class.isInstance(service)).isTrue();

            final ClassLoader proxyLoader = service.getClass().getClassLoader();
            assertThat(proxyLoader).isNotNull();

            final Class<?> classFromParent = proxyLoader.loadClass(GreetingService.class.getName());
            assertThat(classFromParent).isEqualTo(GreetingService.class);

            assertThatExceptionOfType(ClassNotFoundException.class)
                    .isThrownBy(() -> proxyLoader.loadClass("example.hk2.DoesNotExist"));

            final URL missingResource = proxyLoader.getResource("example/hk2/missing-resource.txt");
            assertThat(missingResource).isNull();
        } finally {
            locator.shutdown();
        }
    }

    public interface GreetingService {
        String greet();
    }

}
