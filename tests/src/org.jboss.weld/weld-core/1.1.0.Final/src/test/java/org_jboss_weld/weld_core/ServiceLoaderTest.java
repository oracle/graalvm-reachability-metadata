/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.jboss.weld.bootstrap.spi.Metadata;
import org.jboss.weld.util.ServiceLoader;
import org.junit.jupiter.api.Test;

public class ServiceLoaderTest {
    @Test
    void discoversProviderAndReloadsItsInstance() {
        ServiceLoader<GreetingService> loader = ServiceLoader.load(GreetingService.class);
        List<GreetingService> first = providers(loader);
        assertThat(first).isNotEmpty().allSatisfy(provider ->
                assertThat(provider.greet("Ada")).isEqualTo("Welcome Ada"));
        assertThat(providers(loader)).containsExactlyInAnyOrderElementsOf(first);
        loader.reload();
        List<GreetingService> reloaded = providers(loader);
        assertThat(reloaded).hasSameSizeAs(first).doesNotContainAnyElementsOf(first)
                .allSatisfy(provider -> assertThat(provider.greet("Grace")).isEqualTo("Welcome Grace"));
    }

    private static List<GreetingService> providers(ServiceLoader<GreetingService> loader) {
        List<GreetingService> providers = new ArrayList<>();
        for (Metadata<GreetingService> provider : loader) {
            assertThat(provider.getLocation()).contains("META-INF/services/", GreetingService.class.getName());
            providers.add(provider.getValue());
        }
        return providers;
    }

    public static class Provider implements GreetingService {
        @Override
        public String greet(String name) {
            return "Welcome " + name;
        }
    }
}
