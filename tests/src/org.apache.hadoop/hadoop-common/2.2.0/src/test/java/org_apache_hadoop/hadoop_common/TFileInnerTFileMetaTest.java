/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;

import org.apache.hadoop.io.RawComparator;
import org.apache.hadoop.io.WritableComparator;
import org.apache.hadoop.io.file.tfile.RawComparable;
import org.apache.hadoop.io.file.tfile.TFile;
import org.junit.jupiter.api.Test;

public class TFileInnerTFileMetaTest {
    @Test
    void makeComparatorInstantiatesConfiguredRawComparatorClass() {
        Comparator<RawComparable> comparator = TFile.makeComparator(
                TFile.COMPARATOR_JCLASS + ReverseLexicographicComparator.class.getName());

        RawComparable alpha = rawComparable("alpha");
        RawComparable beta = rawComparable("beta");

        assertThat(comparator.compare(alpha, beta)).isGreaterThan(0);
        assertThat(comparator.compare(beta, alpha)).isLessThan(0);
        assertThat(comparator.compare(alpha, rawComparable("alpha"))).isZero();
    }

    private static RawComparable rawComparable(String value) {
        return new ByteArrayRawComparable(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class ByteArrayRawComparable implements RawComparable {
        private final byte[] bytes;

        private ByteArrayRawComparable(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public byte[] buffer() {
            return bytes;
        }

        @Override
        public int offset() {
            return 0;
        }

        @Override
        public int size() {
            return bytes.length;
        }
    }

    public static final class ReverseLexicographicComparator implements RawComparator<Object> {
        @Override
        public int compare(byte[] left, int leftOffset, int leftLength,
                byte[] right, int rightOffset, int rightLength) {
            return -WritableComparator.compareBytes(
                    left, leftOffset, leftLength, right, rightOffset, rightLength);
        }

        @Override
        public int compare(Object left, Object right) {
            throw new UnsupportedOperationException("Object comparison is not supported");
        }
    }
}
