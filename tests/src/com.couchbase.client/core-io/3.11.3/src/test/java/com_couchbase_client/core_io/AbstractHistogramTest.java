/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.org.HdrHistogram.Histogram;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AbstractHistogramTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Histogram original = new Histogram(100000, 3);
        original.recordValueWithCount(42, 3);
        original.recordValue(700);
        ByteBuffer buffer = ByteBuffer.allocate(original.getNeededByteBufferCapacity());
        original.encodeIntoByteBuffer(buffer);
        buffer.flip();
        Histogram decoded = Histogram.decodeFromByteBuffer(buffer, 0);
        assertEquals(4, decoded.getTotalCount());
        assertEquals(3, decoded.getCountAtValue(42));
        assertEquals(original.getMaxValue(), decoded.getMaxValue());
    }
}
