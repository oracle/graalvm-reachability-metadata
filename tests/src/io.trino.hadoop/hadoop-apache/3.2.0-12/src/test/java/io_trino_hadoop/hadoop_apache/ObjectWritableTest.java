/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino_hadoop.hadoop_apache;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import org.apache.hadoop.io.ObjectWritable;
import org.apache.hadoop.security.proto.SecurityProtos.CancelDelegationTokenResponseProto;
import org.junit.jupiter.api.Test;

public class ObjectWritableTest {
    @Test
    void readObjectRestoresArraysWrittenWithDeclaredClass() throws Exception {
        String[] values = new String[] {"alpha", "beta"};
        byte[] serialized = serialize(values, String[].class);

        Object result = ObjectWritable.readObject(
                new DataInputStream(new ByteArrayInputStream(serialized)),
                null);

        assertThat(result).isInstanceOf(String[].class);
        assertThat((String[]) result).containsExactly(values);
    }

    @Test
    void readObjectRestoresProtocolBufferMessages() throws Exception {
        CancelDelegationTokenResponseProto message =
                CancelDelegationTokenResponseProto.getDefaultInstance();
        byte[] serialized = serialize(message, CancelDelegationTokenResponseProto.class);

        Object result = ObjectWritable.readObject(
                new DataInputStream(new ByteArrayInputStream(serialized)),
                null);

        assertThat(result).isEqualTo(message);
    }

    private static byte[] serialize(Object instance, Class<?> declaredClass) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        ObjectWritable.writeObject(output, instance, declaredClass, null);
        output.flush();
        return bytes.toByteArray();
    }
}
