/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hdrhistogram.HdrHistogram;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;

import org.HdrHistogram.AtomicHistogram;
import org.HdrHistogram.ConcurrentDoubleHistogram;
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.DoubleHistogram;
import org.HdrHistogram.DoubleRecorder;
import org.HdrHistogram.EncodableHistogram;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.IntCountsHistogram;
import org.HdrHistogram.ShortCountsHistogram;
import org.HdrHistogram.SynchronizedDoubleHistogram;
import org.HdrHistogram.SynchronizedHistogram;
import org.junit.jupiter.api.Test;

public class HdrHistogramTest {
    @Test
    void decodesCompressedHistogramsAcrossSupportedVariants() throws Exception {
        // HdrHistogram 2.1.8 predates the Packed* histogram variants that were added later.
        encodeDecode(new AtomicHistogram(10, 0), AtomicHistogram.class, AtomicHistogram::decodeFromCompressedByteBuffer);
        encodeDecode(new ConcurrentDoubleHistogram(0), DoubleHistogram.class,
                ConcurrentDoubleHistogram::decodeFromCompressedByteBuffer);
        encodeDecode(new ConcurrentHistogram(0), ConcurrentHistogram.class,
                ConcurrentHistogram::decodeFromCompressedByteBuffer);
        encodeDecode(new DoubleHistogram(0), DoubleHistogram.class, DoubleHistogram::decodeFromCompressedByteBuffer);
        encodeDecode(new Histogram(0), Histogram.class, Histogram::decodeFromCompressedByteBuffer);
        encodeDecode(new IntCountsHistogram(0), IntCountsHistogram.class, IntCountsHistogram::decodeFromCompressedByteBuffer);
        encodeDecode(new ShortCountsHistogram(0), ShortCountsHistogram.class,
                ShortCountsHistogram::decodeFromCompressedByteBuffer);
        encodeDecode(new SynchronizedDoubleHistogram(0), DoubleHistogram.class,
                SynchronizedDoubleHistogram::decodeFromCompressedByteBuffer);
        encodeDecode(new SynchronizedHistogram(0), SynchronizedHistogram.class,
                SynchronizedHistogram::decodeFromCompressedByteBuffer);
    }

    @Test
    void createsIntervalHistogramUsingReplacementConstructor() {
        new DoubleRecorder(0).getIntervalHistogramInto(new DoubleHistogram(0));
    }

    private void encodeDecode(EncodableHistogram histogram, Class<?> expectedDecodedType, Decoder decoder)
            throws DataFormatException {
        ByteBuffer buffer = ByteBuffer.allocate(histogram.getNeededByteBufferCapacity());
        histogram.encodeIntoCompressedByteBuffer(buffer, 0);
        buffer.flip();
        Object decoded = decoder.decode(buffer, 0);
        assertThat(decoded).isInstanceOf(expectedDecodedType);
    }

    private interface Decoder {
        Object decode(ByteBuffer buffer, long minBarForHighestTrackableValue) throws DataFormatException;
    }
}
