/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twelvemonkeys_common.common_lang;

import com.twelvemonkeys.util.service.ServiceRegistry;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

public class ServiceRegistryTest {
    @Test
    void discoversAndInstantiatesClasspathProvider() {
        ExposedRegistry registry = new ExposedRegistry();

        registry.registerApplicationClasspathSPIs();

        Iterator<GreetingService> providers = registry.greetingProviders();
        assertThat(providers.hasNext()).isTrue();
        assertThat(providers.next().greet("native")).isEqualTo("Hello, native");
        assertThat(providers.hasNext()).isFalse();
    }

    public interface GreetingService {
        String greet(String name);
    }

    public static class GreetingProvider implements GreetingService {
        public GreetingProvider() {
        }

        @Override
        public String greet(String name) {
            return "Hello, " + name;
        }
    }

    public static class ExposedRegistry extends ServiceRegistry {
        public ExposedRegistry() {
            super(Collections.<Class<?>>singleton(GreetingService.class).iterator());
        }

        public Iterator<GreetingService> greetingProviders() {
            return providers(GreetingService.class);
        }
    }
}
