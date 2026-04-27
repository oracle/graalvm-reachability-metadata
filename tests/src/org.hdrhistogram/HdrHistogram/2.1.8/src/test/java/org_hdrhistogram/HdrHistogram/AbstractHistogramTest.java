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

import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.Test;

public class AbstractHistogramTest {
    @Test
    void decodesHistogramFromEncodedByteBuffer() {
        Histogram histogram = new Histogram(1, 3_600_000, 3);
        histogram.recordValueWithCount(42, 4);
        histogram.recordValue(1_000_000);

        ByteBuffer buffer = ByteBuffer.allocate(histogram.getNeededByteBufferCapacity());
        int encodedLength = histogram.encodeIntoByteBuffer(buffer);
        buffer.flip();

        Histogram decoded = Histogram.decodeFromByteBuffer(buffer, 0);

        assertThat(encodedLength).isPositive();
        assertThat(decoded.getTotalCount()).isEqualTo(histogram.getTotalCount());
        assertThat(decoded.getCountAtValue(42)).isEqualTo(4);
        assertThat(decoded.getCountAtValue(1_000_000)).isEqualTo(1);
        assertThat(decoded.getNumberOfSignificantValueDigits())
                .isEqualTo(histogram.getNumberOfSignificantValueDigits());
        assertThat(decoded.getHighestTrackableValue()).isEqualTo(histogram.getHighestTrackableValue());
    }

    @Test
    void decodesCompressedHistogramFromDirectByteBuffer() throws DataFormatException {
        Histogram histogram = new Histogram(1, 10_000, 2);
        histogram.recordValue(25);
        histogram.recordValueWithCount(5_000, 3);

        ByteBuffer buffer = ByteBuffer.allocateDirect(histogram.getNeededByteBufferCapacity() * 2);
        int encodedLength = histogram.encodeIntoCompressedByteBuffer(buffer);
        buffer.flip();

        Histogram decoded = Histogram.decodeFromCompressedByteBuffer(buffer, 0);

        assertThat(encodedLength).isPositive();
        assertThat(decoded.getTotalCount()).isEqualTo(histogram.getTotalCount());
        assertThat(decoded.getCountAtValue(25)).isEqualTo(1);
        assertThat(decoded.getCountAtValue(5_000)).isEqualTo(3);
        assertThat(decoded.getLowestDiscernibleValue()).isEqualTo(histogram.getLowestDiscernibleValue());
    }
}
