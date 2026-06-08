/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableComparator;
import org.junit.jupiter.api.Test;

public class WritableComparatorTest {
    @Test
    void getInitializesWritableClassBeforeReturningRegisteredComparator() {
        WritableComparator comparator = WritableComparator.get(ComparableValue.class);

        assertThat(comparator).isInstanceOf(ComparableValueComparator.class);
        assertThat(comparator.compare(new ComparableValue(7), new ComparableValue(3))).isLessThan(0);
        assertThat(comparator.compare(new ComparableValue(3), new ComparableValue(7))).isGreaterThan(0);
    }

    public static class ComparableValue implements WritableComparable<ComparableValue> {
        static {
            WritableComparator.define(ComparableValue.class, new ComparableValueComparator());
        }

        private int value;

        public ComparableValue() {
        }

        ComparableValue(int value) {
            this.value = value;
        }

        @Override
        public void write(DataOutput out) throws IOException {
            out.writeInt(value);
        }

        @Override
        public void readFields(DataInput in) throws IOException {
            value = in.readInt();
        }

        @Override
        public int compareTo(ComparableValue other) {
            return Integer.compare(value, other.value);
        }
    }

    public static class ComparableValueComparator extends WritableComparator {
        public ComparableValueComparator() {
            super(ComparableValue.class);
        }

        @Override
        public int compare(WritableComparable first, WritableComparable second) {
            ComparableValue firstValue = (ComparableValue) first;
            ComparableValue secondValue = (ComparableValue) second;
            return -firstValue.compareTo(secondValue);
        }
    }
}
