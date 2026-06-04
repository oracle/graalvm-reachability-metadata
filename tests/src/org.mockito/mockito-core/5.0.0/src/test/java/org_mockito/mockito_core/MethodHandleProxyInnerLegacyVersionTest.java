/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.withSettings;

public class MethodHandleProxyInnerLegacyVersionTest {
    public interface DefaultGreetingService {
        String prefix();

        default String greeting(String name) {
            return prefix() + ", " + name;
        }
    }

    @Test
    void defaultInterfaceMethodsCanCallOtherMockedInterfaceMethods() {
        DefaultGreetingService service = Mockito.mock(
                DefaultGreetingService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        doReturn("Hello").when(service).prefix();

        assertThat(service.greeting("GraalVM")).isEqualTo("Hello, GraalVM");
    }
}
