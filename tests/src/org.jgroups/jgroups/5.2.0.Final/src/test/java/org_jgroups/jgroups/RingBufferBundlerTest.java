/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.protocols.RingBufferBundler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

public class RingBufferBundlerTest {
    @BeforeEach
    void resetRecorder() {
        RecordingWaitStrategy.reset();
    }

    @Test
    void waitStrategyInstantiatesNamedBiConsumer() {
        RingBufferBundler bundler = new RingBufferBundler(8);

        RingBufferBundler configured = bundler.waitStrategy(RecordingWaitStrategy.class.getName());

        assertThat(configured).isSameAs(bundler);
        assertThat(RecordingWaitStrategy.instances()).isEqualTo(1);
        assertThat(bundler.waitStrategy()).isEqualTo("RecordingWaitStrategy");
    }

    public static class RecordingWaitStrategy implements BiConsumer<Integer, Integer> {
        private static final AtomicInteger INSTANCES = new AtomicInteger();

        public RecordingWaitStrategy() {
            INSTANCES.incrementAndGet();
        }

        public static void reset() {
            INSTANCES.set(0);
        }

        public static int instances() {
            return INSTANCES.get();
        }

        @Override
        public void accept(Integer iteration, Integer spins) {
        }
    }
}
