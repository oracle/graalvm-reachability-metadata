/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.org.HdrHistogram.DoubleHistogram;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DoubleHistogramTest {
    @Test
    void exercisesPublicApi() throws Exception {
        DoubleHistogram original = new DoubleHistogram(3);
        original.recordValue(1.25);
        original.recordValueWithCount(8.5, 4);
        DoubleHistogram copied = new DoubleHistogram(original);
        assertEquals(0, copied.getTotalCount());
        assertEquals(original.getNumberOfSignificantValueDigits(), copied.getNumberOfSignificantValueDigits());
        original.copyInto(copied);
        assertEquals(5, copied.getTotalCount());
        ByteBuffer buffer = ByteBuffer.allocate(original.getNeededByteBufferCapacity());
        original.encodeIntoByteBuffer(buffer);
        buffer.flip();
        DoubleHistogram decoded = DoubleHistogram.decodeFromByteBuffer(buffer, 0);
        assertEquals(original.getTotalCount(), decoded.getTotalCount());
        assertEquals(original.getCountAtValue(8.5), decoded.getCountAtValue(8.5));
        DoubleHistogram restored = SerializationSupport.roundTrip(original);
        assertEquals(original.getTotalCount(), restored.getTotalCount());
        assertEquals(original.getCountAtValue(1.25), restored.getCountAtValue(1.25));
    }
}
