/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.protocols.RingBufferBundler;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

public class RingBufferBundlerTest {
    @Test
    void configuresCustomWaitStrategyByClassName() {
        CountingWaitStrategy.calls.set(0);
        RingBufferBundler bundler = new RingBufferBundler(16);

        RingBufferBundler configured = bundler.waitStrategy(CountingWaitStrategy.class.getName());

        assertThat(configured).isSameAs(bundler);
        assertThat(bundler.waitStrategy()).isEqualTo("CountingWaitStrategy");
    }

    public static class CountingWaitStrategy implements BiConsumer<Integer, Integer> {
        private static final AtomicInteger calls = new AtomicInteger();

        @Override
        public void accept(Integer iteration, Integer spins) {
            calls.incrementAndGet();
        }
    }
}
