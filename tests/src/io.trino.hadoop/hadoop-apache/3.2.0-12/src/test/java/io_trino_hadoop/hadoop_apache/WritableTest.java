/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino_hadoop.hadoop_apache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.hadoop.io.ArrayWritable;
import org.apache.hadoop.io.DataInputBuffer;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableUtils;
import org.junit.jupiter.api.Test;

public class WritableTest {
    @Test
    void arrayWritableCreatesTypedWritableArray() {
        ArrayWritable writable = new ArrayWritable(IntWritable.class, new Writable[] {
            new IntWritable(7),
            new IntWritable(11)
        });

        Object result = writable.toArray();

        assertThat(result).isInstanceOf(IntWritable[].class);
        assertThat((IntWritable[]) result).extracting(IntWritable::get).containsExactly(7, 11);
    }

    @Test
    void mapWritableRoundTripsDeclaredWritableTypes() throws Exception {
        MapWritable expected = new MapWritable();
        expected.put(new Text("first"), new IntWritable(1));
        expected.put(new Text("second"), new Text("two"));

        DataOutputBuffer output = new DataOutputBuffer();
        expected.write(output);
        DataInputBuffer input = new DataInputBuffer();
        input.reset(output.getData(), output.getLength());
        MapWritable actual = new MapWritable();
        actual.readFields(input);

        assertThat(actual.entrySet())
                .extracting(entry -> Map.entry(
                        entry.getKey().toString(),
                        entry.getValue().toString()))
                .containsExactlyInAnyOrder(Map.entry("first", "1"), Map.entry("second", "two"));
    }

    @Test
    void writableUtilsRoundTripsCompressedStringArrays() throws Exception {
        String[] expected = new String[] {"alpha", "beta", "gamma"};
        DataOutputBuffer output = new DataOutputBuffer();

        WritableUtils.writeCompressedStringArray(output, expected);
        DataInputBuffer input = new DataInputBuffer();
        input.reset(output.getData(), output.getLength());

        assertThat(WritableUtils.readCompressedStringArray(input)).containsExactly(expected);
    }
}
