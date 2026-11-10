/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lmax.disruptor;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.MultiProducerSequencer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.Sequencer;
import com.lmax.disruptor.SingleProducerSequencer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractSequencerTest {
    @Test
    void gatesSingleProducerOnConsumerProgress() throws InsufficientCapacityException {
        assertConsumerGating(new SingleProducerSequencer(4, new BlockingWaitStrategy()));
    }

    @Test
    void gatesMultipleProducersOnConsumerProgress() throws InsufficientCapacityException {
        assertConsumerGating(new MultiProducerSequencer(4, new BlockingWaitStrategy()));
    }

    private static void assertConsumerGating(Sequencer sequencer) throws InsufficientCapacityException {
        Sequence consumer = new Sequence();
        sequencer.addGatingSequences(consumer);
        assertEquals(-1L, consumer.get());
        assertEquals(4L, sequencer.remainingCapacity());

        long last = sequencer.tryNext(4);
        assertEquals(3L, last);
        sequencer.publish(0L, last);
        assertEquals(last, sequencer.getCursor());
        assertEquals(0L, sequencer.remainingCapacity());
        assertFalse(sequencer.hasAvailableCapacity(1));
        assertThrows(InsufficientCapacityException.class, sequencer::tryNext);

        consumer.set(1L);
        assertEquals(1L, sequencer.getMinimumSequence());
        assertEquals(2L, sequencer.remainingCapacity());
        long next = sequencer.tryNext();
        assertEquals(4L, next);
        sequencer.publish(next);
        assertEquals(1L, sequencer.remainingCapacity());

        assertTrue(sequencer.removeGatingSequence(consumer));
        assertFalse(sequencer.removeGatingSequence(consumer));
        assertEquals(4L, sequencer.remainingCapacity());
        assertEquals(next, sequencer.getMinimumSequence());
    }
}
