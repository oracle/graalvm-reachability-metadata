/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.CALLS_REAL_METHODS;

public class MethodHandleProxyTest {
    @Test
    void proxyMockMakerCanCallDefaultInterfaceMethod() {
        DefaultGreeting greeting =
                Mockito.mock(
                        DefaultGreeting.class,
                        Mockito.withSettings()
                                .mockMaker(MockMakers.PROXY)
                                .defaultAnswer(CALLS_REAL_METHODS));

        assertThat(greeting.greet("Mockito")).isEqualTo("Hello Mockito");
    }

    public interface DefaultGreeting {
        default String greet(String name) {
            return "Hello " + name;
        }
    }
}
