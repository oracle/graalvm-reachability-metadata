/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.jgroups.protocols.RingBufferBundler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RingBufferBundlerTest {
    @Test
    void createsCustomWaitStrategyFromConfiguredClassName() {
        RecordingWaitStrategy.reset();
        RingBufferBundler bundler = new RingBufferBundler(16);

        bundler.waitStrategy(RecordingWaitStrategy.class.getName());

        assertThat(RecordingWaitStrategy.constructorCalls()).hasValue(1);
        assertThat(bundler.waitStrategy()).isEqualTo(RecordingWaitStrategy.class.getSimpleName());
    }

    public static class RecordingWaitStrategy implements BiConsumer<Integer, Integer> {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();
        private static final AtomicInteger ACCEPT_CALLS = new AtomicInteger();

        public RecordingWaitStrategy() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }

        static void reset() {
            CONSTRUCTOR_CALLS.set(0);
            ACCEPT_CALLS.set(0);
        }

        static AtomicInteger constructorCalls() {
            return CONSTRUCTOR_CALLS;
        }

        static AtomicInteger acceptCalls() {
            return ACCEPT_CALLS;
        }

        @Override
        public void accept(Integer iteration, Integer spins) {
            ACCEPT_CALLS.incrementAndGet();
        }
    }
}
