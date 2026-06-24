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

import org.apache.hadoop.io.ArrayPrimitiveWritable;
import org.junit.jupiter.api.Test;

public class ArrayPrimitiveWritableTest {
    @Test
    void readFieldsRestoresPrimitiveArray() throws IOException {
        int[] expected = new int[] {3, 5, 8, 13};
        ArrayPrimitiveWritable original = new ArrayPrimitiveWritable(expected);
        byte[] serialized = serialize(original);

        ArrayPrimitiveWritable restored = new ArrayPrimitiveWritable();
        restored.readFields(new DataInputStream(new ByteArrayInputStream(serialized)));

        assertThat(restored.getComponentType()).isEqualTo(Integer.TYPE);
        assertThat(restored.get()).isInstanceOf(int[].class);
        assertThat((int[]) restored.get()).containsExactly(expected);
    }

    private static byte[] serialize(ArrayPrimitiveWritable writable) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        writable.write(out);
        out.flush();
        return bytes.toByteArray();
    }
}
