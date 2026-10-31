/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;

import org.jboss.weld.bootstrap.spi.Metadata;
import org.jboss.weld.util.ServiceLoader;
import org.junit.jupiter.api.Test;

public class ServiceLoaderTest {
    @Test
    void discoversProviderAndReloadsItsInstance() {
        ServiceLoader<GreetingService> loader = ServiceLoader.load(GreetingService.class);
        Metadata<GreetingService> first = onlyProvider(loader);
        assertThat(first.getValue().greet("Ada")).isEqualTo("Welcome Ada");
        assertThat(first.getLocation()).contains("META-INF/services/", GreetingService.class.getName());
        assertThat(onlyProvider(loader).getValue()).isSameAs(first.getValue());
        loader.reload();
        Metadata<GreetingService> reloaded = onlyProvider(loader);
        assertThat(reloaded.getValue()).isNotSameAs(first.getValue());
        assertThat(reloaded.getValue().greet("Grace")).isEqualTo("Welcome Grace");
    }

    private static Metadata<GreetingService> onlyProvider(ServiceLoader<GreetingService> loader) {
        Iterator<Metadata<GreetingService>> providers = loader.iterator();
        assertThat(providers.hasNext()).isTrue();
        Metadata<GreetingService> provider = providers.next();
        assertThat(providers.hasNext()).isFalse();
        return provider;
    }

    public static class Provider implements GreetingService {
        @Override
        public String greet(String name) {
            return "Welcome " + name;
        }
    }
}
