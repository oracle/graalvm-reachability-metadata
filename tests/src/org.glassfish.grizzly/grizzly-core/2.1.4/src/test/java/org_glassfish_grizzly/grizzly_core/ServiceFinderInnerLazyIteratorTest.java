/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_core;

import org.glassfish.grizzly.utils.ServiceFinder;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ServiceFinderInnerLazyIteratorTest {
    @Test
    void discoversAProviderOnceAndInvokesItsService() {
        Iterator<GreetingService> providers = ServiceFinder.find(
                GreetingService.class, ServiceFinderInnerLazyIteratorTest.class.getClassLoader()).iterator();

        assertThat(providers.hasNext()).isTrue();
        assertThat(providers.hasNext()).isTrue();
        GreetingService service = providers.next();
        assertThat(service.greet("reader")).isEqualTo("Hello, reader!");
        assertThat(providers.hasNext()).isFalse();
        assertThatThrownBy(providers::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void systemResourceLookupReturnsNoProvidersForAnUnregisteredService() {
        Iterator<UnregisteredService> providers = ServiceFinder.find(UnregisteredService.class, null).iterator();

        assertThat(providers.hasNext()).isFalse();
        assertThatThrownBy(providers::next).isInstanceOf(NoSuchElementException.class);
    }

    public interface GreetingService {
        String greet(String recipient);
    }

    public static class GreetingProvider implements GreetingService {
        @Override
        public String greet(String recipient) {
            return "Hello, " + recipient + "!";
        }
    }

    public interface UnregisteredService {
        String greet(String recipient);
    }
}
