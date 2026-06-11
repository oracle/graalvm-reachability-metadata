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

public class InvokeDefaultProxyInnerInvokeDefaultRealMethodTest {
    @Test
    void proxyMockMakerDispatchesToDefaultMethodRealImplementation() {
        Counter counter =
                Mockito.mock(
                        Counter.class,
                        Mockito.withSettings()
                                .mockMaker(MockMakers.PROXY)
                                .defaultAnswer(CALLS_REAL_METHODS));

        assertThat(counter.increment(4)).isEqualTo(5);
    }

    public interface Counter {
        default int increment(int value) {
            return value + 1;
        }
    }
}
