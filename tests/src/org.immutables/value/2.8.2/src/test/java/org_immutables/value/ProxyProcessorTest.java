/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_immutables.value;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

import org.eclipse.fake.ProxyProcessorCaller;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProxyProcessorTest {

    private static final String OSGI_ARCH_PROPERTY = "osgi.arch";

    @Test
    void proxyProcessorRemainsUsableWhenEclipseLikeDelegationConditionsArePresent() throws Exception {
        String previousOsgiArch = System.getProperty(OSGI_ARCH_PROPERTY);
        Thread currentThread = Thread.currentThread();
        ClassLoader previousContextClassLoader = currentThread.getContextClassLoader();

        try (URLClassLoader alternateContextClassLoader = new URLClassLoader(
                new URL[0],
                previousContextClassLoader == null ? ClassLoader.getPlatformClassLoader() : previousContextClassLoader
        )) {
            System.setProperty(OSGI_ARCH_PROPERTY, "test-arch");
            currentThread.setContextClassLoader(alternateContextClassLoader);

            Set<String> supportedAnnotationTypes = ProxyProcessorCaller.loadSupportedAnnotationTypes();

            assertThat(supportedAnnotationTypes)
                    .isNotEmpty()
                    .allSatisfy(annotationType -> assertThat(annotationType).isNotBlank());
        } finally {
            currentThread.setContextClassLoader(previousContextClassLoader);
            restoreSystemProperty(previousOsgiArch);
        }
    }

    private static void restoreSystemProperty(String previousValue) {
        if (previousValue == null) {
            System.clearProperty(OSGI_ARCH_PROPERTY);
        } else {
            System.setProperty(OSGI_ARCH_PROPERTY, previousValue);
        }
    }
}
