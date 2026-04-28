/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_reactivex_rxjava3.rxjava;

import io.reactivex.rxjava3.processors.ReplayProcessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReplayProcessorInnerSizeBoundReplayBufferTest {
    @Test
    void typedValuesSnapshotAllocatesArrayForRetainedItems() {
        ReplayProcessor<String> processor = ReplayProcessor.createWithSize(2);

        processor.onNext("alpha");
        processor.onNext("beta");
        processor.onNext("gamma");

        String[] values = processor.getValues(new String[0]);

        assertEquals(String[].class, values.getClass());
        assertArrayEquals(new String[] {"beta", "gamma"}, values);
    }
}
