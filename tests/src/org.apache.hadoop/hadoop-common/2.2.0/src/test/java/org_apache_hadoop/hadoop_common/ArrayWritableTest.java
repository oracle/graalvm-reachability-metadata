/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.io.ArrayWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Writable;
import org.junit.jupiter.api.Test;

public class ArrayWritableTest {
    @Test
    void toArrayReturnsTypedWritableArray() {
        ArrayWritable writable = new ArrayWritable(IntWritable.class, new Writable[] {
            new IntWritable(7),
            new IntWritable(11)
        });

        Object result = writable.toArray();

        assertThat(result).isInstanceOf(IntWritable[].class);
        IntWritable[] values = (IntWritable[]) result;
        assertThat(values).extracting(IntWritable::get).containsExactly(7, 11);
    }
}
