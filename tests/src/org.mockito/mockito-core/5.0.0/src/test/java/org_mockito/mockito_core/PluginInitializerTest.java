/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.plugins.MockResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PluginInitializerTest {
    @Test
    void loadsMockResolverPluginsFromMockitoExtensionsAndUsesThem() {
        GreetingService mock = Mockito.mock(GreetingService.class);
        GreetingService wrappedMock = new GreetingServiceWrapper(mock);
        when(mock.greetingFor("Mockito")).thenReturn("Hello Mockito");

        String greeting = wrappedMock.greetingFor("Mockito");

        assertThat(greeting).isEqualTo("Hello Mockito");
        assertThat(Mockito.mockingDetails(wrappedMock).isMock()).isTrue();
        verify(wrappedMock).greetingFor("Mockito");
    }

    public static class WrappingMockResolver implements MockResolver {
        @Override
        public Object resolve(Object instance) {
            if (instance instanceof WrappedMock) {
                return ((WrappedMock) instance).mock();
            }
            return instance;
        }
    }

    private interface WrappedMock {
        Object mock();
    }

    private interface GreetingService {
        String greetingFor(String name);
    }

    private static final class GreetingServiceWrapper implements GreetingService, WrappedMock {
        private final GreetingService mock;

        private GreetingServiceWrapper(GreetingService mock) {
            this.mock = mock;
        }

        @Override
        public String greetingFor(String name) {
            return mock.greetingFor(name);
        }

        @Override
        public Object mock() {
            return mock;
        }
    }
}
