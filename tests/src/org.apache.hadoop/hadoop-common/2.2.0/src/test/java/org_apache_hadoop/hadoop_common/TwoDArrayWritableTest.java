/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.TwoDArrayWritable;
import org.apache.hadoop.io.Writable;
import org.junit.jupiter.api.Test;

public class TwoDArrayWritableTest {
    @Test
    void toArrayReturnsTypedTwoDimensionalArray() {
        TwoDArrayWritable writable = new TwoDArrayWritable(IntWritable.class, new Writable[][] {
            {new IntWritable(1), new IntWritable(2)},
            {new IntWritable(3)}
        });

        Object result = writable.toArray();

        assertThat(result).isInstanceOf(IntWritable[][].class);
        IntWritable[][] values = (IntWritable[][]) result;
        assertThat(values.length).isEqualTo(2);
        assertThat(values[0]).extracting(IntWritable::get).containsExactly(1, 2);
        assertThat(values[1]).extracting(IntWritable::get).containsExactly(3);
    }

    @Test
    void readFieldsRestoresWritableMatrix() throws IOException {
        TwoDArrayWritable original = new TwoDArrayWritable(IntWritable.class, new Writable[][] {
            {new IntWritable(4)},
            {new IntWritable(5), new IntWritable(6)}
        });
        byte[] serialized = serialize(original);

        TwoDArrayWritable restored = new TwoDArrayWritable(IntWritable.class);
        restored.readFields(new DataInputStream(new ByteArrayInputStream(serialized)));

        Writable[][] values = restored.get();
        assertThat(values.length).isEqualTo(2);
        assertThat(values[0]).hasSize(1);
        assertThat(((IntWritable) values[0][0]).get()).isEqualTo(4);
        assertThat(values[1]).hasSize(2);
        assertThat(((IntWritable) values[1][0]).get()).isEqualTo(5);
        assertThat(((IntWritable) values[1][1]).get()).isEqualTo(6);
    }

    private static byte[] serialize(TwoDArrayWritable writable) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        writable.write(out);
        out.flush();
        return bytes.toByteArray();
    }
}
