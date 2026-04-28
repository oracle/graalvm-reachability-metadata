/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_proxytoys.proxytoys;

import static org.assertj.core.api.Assertions.assertThat;

import com.thoughtworks.proxy.toys.multicast.Multicast;
import com.thoughtworks.proxy.toys.multicast.Multicasting;
import org.junit.jupiter.api.Test;

public class MulticastingInvokerTest {
    @Test
    public void typedTargetArrayReturnsOriginalTargets() {
        Counter first = new FixedCounter(2);
        Counter second = new FixedCounter(3);
        Counter proxy = Multicasting.proxy(Counter.class)
                .with(first, second)
                .build();

        Counter[] targets = ((Multicast) proxy).getTargetsInArray(Counter.class);

        assertThat(targets).containsExactly(first, second);
    }

    @Test
    public void proxyInvocationCallsEachCompatibleTargetAndCombinesPrimitiveResults() {
        Counter proxy = Multicasting.proxy(Counter.class)
                .with(new FixedCounter(7), new FixedCounter(11))
                .build();

        assertThat(proxy.count()).isEqualTo(18);
    }

    public interface Counter {
        int count();
    }

    private static final class FixedCounter implements Counter {
        private final int count;

        private FixedCounter(int count) {
            this.count = count;
        }

        @Override
        public int count() {
            return count;
        }
    }
}
