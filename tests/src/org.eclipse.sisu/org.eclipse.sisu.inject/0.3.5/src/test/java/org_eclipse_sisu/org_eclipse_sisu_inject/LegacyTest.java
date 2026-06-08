/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;

import org.eclipse.sisu.inject.Legacy;
import org.junit.jupiter.api.Test;

public class LegacyTest {
    @Test
    void proxyForwardsInterfaceCallsToDelegate() {
        registerLegacyBeanEntryProxyInterface();
        GreetingService delegate = new GreetingService() {
            @Override
            public String greet(String name) {
                return "Hello, " + name;
            }

            @Override
            public int invocationCount() {
                return 1;
            }
        };

        GreetingService proxy = Legacy.as(GreetingService.class).proxy(delegate);

        assertThat(proxy).isNotSameAs(delegate);
        assertThat(proxy.greet("Sisu")).isEqualTo("Hello, Sisu");
        assertThat(proxy.invocationCount()).isEqualTo(1);
    }

    @Test
    void proxyReturnsNullForNullDelegate() {
        registerLegacyBeanEntryProxyInterface();
        GreetingService proxy = Legacy.as(GreetingService.class).proxy(null);

        assertThat(proxy).isNull();
    }

    private static void registerLegacyBeanEntryProxyInterface() {
        Object proxy = Proxy.newProxyInstance(
            org.sonatype.inject.BeanEntry.class.getClassLoader(),
            new Class<?>[] {org.sonatype.inject.BeanEntry.class},
            (proxyInstance, method, arguments) -> null);

        assertThat(proxy).isInstanceOf(org.sonatype.inject.BeanEntry.class);
    }

    public interface GreetingService {
        String greet(String name);

        int invocationCount();
    }
}
