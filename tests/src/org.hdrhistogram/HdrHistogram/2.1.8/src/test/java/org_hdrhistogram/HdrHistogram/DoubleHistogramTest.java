/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hdrhistogram.HdrHistogram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.DoubleHistogram;
import org.junit.jupiter.api.Test;

public class DoubleHistogramTest {
    @Test
    void createsHistogramWithCustomInternalCountsHistogram() {
        DoubleHistogram histogram = new DoubleHistogram(3, ConcurrentHistogram.class);

        histogram.recordValueWithCount(42.0, 2);
        histogram.recordValue(84.0);

        assertThat(histogram.getTotalCount()).isEqualTo(3);
        assertThat(histogram.getValueAtPercentile(50.0)).isCloseTo(42.0, within(0.1));
        assertThat(histogram.getValueAtPercentile(100.0)).isCloseTo(84.0, within(0.1));
    }

    @Test
    void copiesHistogramRangeUsingSourceInternalCountsHistogramType() {
        DoubleHistogram source = new DoubleHistogram(3, ConcurrentHistogram.class);
        source.recordValue(0.5);
        source.recordValue(512.0);
        source.setAutoResize(false);

        DoubleHistogram copy = new DoubleHistogram(source);

        assertThat(copy.isAutoResize()).isFalse();
        assertThat(copy.getTotalCount()).isZero();
        assertThat(copy.getNumberOfSignificantValueDigits()).isEqualTo(source.getNumberOfSignificantValueDigits());
    }

    @Test
    void serializesAndDeserializesRecordedValues() throws Exception {
        DoubleHistogram histogram = new DoubleHistogram(3, ConcurrentHistogram.class);
        histogram.recordValueWithCount(1.5, 3);
        histogram.recordValue(99.0);

        byte[] serializedHistogram = serialize(histogram);
        DoubleHistogram deserializedHistogram = deserialize(serializedHistogram);

        assertThat(deserializedHistogram.getTotalCount()).isEqualTo(histogram.getTotalCount());
        assertThat(deserializedHistogram.getValueAtPercentile(50.0)).isCloseTo(1.5, within(0.01));
        assertThat(deserializedHistogram.getValueAtPercentile(100.0)).isCloseTo(99.0, within(0.1));
    }

    private static byte[] serialize(DoubleHistogram histogram) throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(histogram);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private static DoubleHistogram deserialize(byte[] serializedHistogram) throws Exception {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(serializedHistogram);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream)) {
            return (DoubleHistogram) objectInputStream.readObject();
        }
    }
}
