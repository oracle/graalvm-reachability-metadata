/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lmax.disruptor;

import com.lmax.disruptor.Sequence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SequenceTest {
    @Test
    void updatesSequenceThroughOrderedVolatileAndAtomicWrites() {
        Sequence sequence = new Sequence();
        assertEquals(-1L, sequence.get());

        sequence.set(12L);
        assertEquals(12L, sequence.get());
        assertTrue(sequence.compareAndSet(12L, 20L));
        assertFalse(sequence.compareAndSet(12L, 99L));
        assertEquals(20L, sequence.get());

        sequence.setVolatile(30L);
        assertEquals(30L, sequence.get());
        assertEquals(31L, sequence.incrementAndGet());
        assertEquals(36L, sequence.addAndGet(5L));
        assertEquals(36L, sequence.get());
    }
}
