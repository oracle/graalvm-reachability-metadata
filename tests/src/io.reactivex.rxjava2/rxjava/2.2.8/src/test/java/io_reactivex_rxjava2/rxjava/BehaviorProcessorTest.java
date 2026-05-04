/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_reactivex_rxjava2.rxjava;

import io.reactivex.processors.BehaviorProcessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BehaviorProcessorTest {
    @Test
    @SuppressWarnings("deprecation")
    void typedValuesSnapshotAllocatesArrayForCurrentValue() {
        BehaviorProcessor<String> processor = BehaviorProcessor.createDefault("alpha");

        String[] values = processor.getValues(new String[0]);

        assertEquals(String[].class, values.getClass());
        assertArrayEquals(new String[] {"alpha"}, values);
    }
}
