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

import org.apache.hadoop.io.ByteWritable;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.MapWritable;
import org.junit.jupiter.api.Test;

public class AbstractMapWritableTest {
    @Test
    void readFieldsRestoresMapWithDynamicallyRegisteredWritableClasses() throws IOException {
        MapWritable original = new MapWritable();
        ByteWritable key = new ByteWritable((byte) 7);
        DoubleWritable value = new DoubleWritable(3.25D);
        original.put(key, value);

        byte[] serialized = serialize(original);
        MapWritable restored = new MapWritable();
        restored.readFields(new DataInputStream(new ByteArrayInputStream(serialized)));

        assertThat(restored).hasSize(1);
        assertThat(restored.get(key)).isEqualTo(value);
    }

    private static byte[] serialize(MapWritable writable) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        writable.write(output);
        output.flush();
        return bytes.toByteArray();
    }
}
