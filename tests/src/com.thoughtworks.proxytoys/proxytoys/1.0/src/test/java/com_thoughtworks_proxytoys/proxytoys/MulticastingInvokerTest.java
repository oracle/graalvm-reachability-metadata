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
import java.io.Serializable;
import org.junit.jupiter.api.Test;

public class MulticastingInvokerTest {
    @Test
    void returnsTargetsInTypedArray() {
        CountingService first = new FixedCountingService(3);
        CountingService second = new FixedCountingService(5);
        CountingService proxy = Multicasting.proxy(CountingService.class).with(first, second).build();

        CountingService[] targets = ((Multicast) proxy).getTargetsInArray(CountingService.class);

        assertThat(targets).containsExactly(first, second);
    }

    @Test
    void multicastsInterfaceInvocationAcrossAllTargets() {
        CountingService proxy = Multicasting.proxy(CountingService.class)
                .with(new FixedCountingService(7), new FixedCountingService(11))
                .build();

        int count = proxy.count();

        assertThat(count).isEqualTo(18);
    }

    public interface CountingService extends Serializable {
        int count();
    }

    public static final class FixedCountingService implements CountingService {
        private static final long serialVersionUID = 1L;

        private final int count;

        public FixedCountingService(int count) {
            this.count = count;
        }

        @Override
        public int count() {
            return count;
        }
    }
}
