/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package hdrhistogram;

import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;

import org.HdrHistogram.AtomicHistogram;
import org.HdrHistogram.ConcurrentDoubleHistogram;
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.DoubleHistogram;
import org.HdrHistogram.EncodableHistogram;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.IntCountsHistogram;
import org.HdrHistogram.PackedConcurrentDoubleHistogram;
import org.HdrHistogram.PackedConcurrentHistogram;
import org.HdrHistogram.PackedDoubleHistogram;
import org.HdrHistogram.PackedHistogram;
import org.HdrHistogram.ShortCountsHistogram;
import org.HdrHistogram.SynchronizedDoubleHistogram;
import org.HdrHistogram.SynchronizedHistogram;
import org.junit.jupiter.api.Test;

/**
 * @author Moritz Halbritter
 */
class HdrHistogramTest {
    @Test
    void test() throws Exception {
        encodeDecode(new AtomicHistogram(10, 0), AtomicHistogram::decodeFromCompressedByteBuffer);
        encodeDecode(new ConcurrentDoubleHistogram(0), ConcurrentDoubleHistogram::decodeFromCompressedByteBuffer);
        encodeDecode(new ConcurrentHistogram(0), ConcurrentHistogram::decodeFromCompressedByteBuffer);
        encodeDecode(new DoubleHistogram(0), DoubleHistogram::decodeFromCompressedByteBuffer);
        encodeDecode(new Histogram(0), Histogram::decodeFromCompressedByteBuffer);
        encodeDecode(new IntCountsHistogram(0), IntCountsHistogram::decodeFromCompressedByteBuffer);
        encodeDecode(new PackedConcurrentDoubleHistogram(0), PackedConcurrentDoubleHistogram::decodeFromCompressedByteBuffer);
        encodeDecode(new PackedConcurrentHistogram(0), PackedConcurrentHistogram::decodeFromCompressedByteBuffer);
        encodeDecode(new PackedDoubleHistogram(0), PackedDoubleHistogram::decodeFromCompressedByteBuffer);
        encodeDecode(new PackedHistogram(0), PackedHistogram::decodeFromCompressedByteBuffer);
        encodeDecode(new ShortCountsHistogram(0), ShortCountsHistogram::decodeFromCompressedByteBuffer);
        encodeDecode(new SynchronizedDoubleHistogram(0), SynchronizedDoubleHistogram::decodeFromCompressedByteBuffer);
        encodeDecode(new SynchronizedHistogram(0), SynchronizedHistogram::decodeFromCompressedByteBuffer);
    }

    private void encodeDecode(EncodableHistogram histogram, Decoder decoder) throws DataFormatException {
        ByteBuffer buffer = ByteBuffer.allocate(histogram.getNeededByteBufferCapacity());
        histogram.encodeIntoCompressedByteBuffer(buffer, 0);
        buffer.flip();
        Object decoded = decoder.decode(buffer, 0);
        System.out.println(decoded);
    }

    private interface Decoder {
        Object decode(ByteBuffer buffer, long minBarForHighestTrackableValue) throws DataFormatException;
    }
}
