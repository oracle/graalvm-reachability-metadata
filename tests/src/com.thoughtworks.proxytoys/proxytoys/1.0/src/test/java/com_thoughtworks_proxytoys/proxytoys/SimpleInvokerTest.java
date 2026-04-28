/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_proxytoys.proxytoys;

import static org.assertj.core.api.Assertions.assertThat;

import com.thoughtworks.proxy.ProxyFactory;
import com.thoughtworks.proxy.factory.StandardProxyFactory;
import com.thoughtworks.proxy.kit.SimpleInvoker;
import org.junit.jupiter.api.Test;

public class SimpleInvokerTest {
    @Test
    public void proxyCallsAreForwardedToTargetObject() {
        Accumulator target = new CountingAccumulator(3);
        ProxyFactory proxyFactory = new StandardProxyFactory();
        Accumulator proxy = proxyFactory.createProxy(new SimpleInvoker(target), Accumulator.class);

        assertThat(proxy.add(4)).isEqualTo(7);
        assertThat(proxy.add(5)).isEqualTo(12);
        assertThat(proxy.total()).isEqualTo(12);
    }

    public interface Accumulator {
        int add(int value);

        int total();
    }

    private static final class CountingAccumulator implements Accumulator {
        private int total;

        private CountingAccumulator(int initialValue) {
            total = initialValue;
        }

        @Override
        public int add(int value) {
            total += value;
            return total;
        }

        @Override
        public int total() {
            return total;
        }
    }
}
