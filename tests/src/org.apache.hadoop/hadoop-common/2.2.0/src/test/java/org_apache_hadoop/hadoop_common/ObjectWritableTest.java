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
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.apache.hadoop.io.ObjectWritable;
import org.apache.hadoop.security.proto.SecurityProtos.CancelDelegationTokenResponseProto;
import org.junit.jupiter.api.Test;

public class ObjectWritableTest {
    @Test
    void readObjectRestoresArrayWithoutConfiguration() throws IOException {
        String[] values = new String[] {"alpha", "beta"};
        byte[] serialized = serialize(values, String[].class);
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(serialized));

        Object result = ObjectWritable.readObject(input, null);

        assertThat(result).isInstanceOf(String[].class);
        assertThat((String[]) result).containsExactly(values);
    }

    @Test
    void readObjectRestoresProtobufFromInputStreamDataInput() throws IOException {
        CancelDelegationTokenResponseProto message =
                CancelDelegationTokenResponseProto.getDefaultInstance();
        byte[] serialized = serialize(message, CancelDelegationTokenResponseProto.class);
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(serialized));

        Object result = ObjectWritable.readObject(input, null);

        assertThat(result).isEqualTo(message);
    }

    @Test
    void readObjectRestoresProtobufFromNonInputStreamDataInput() throws IOException {
        CancelDelegationTokenResponseProto message =
                CancelDelegationTokenResponseProto.getDefaultInstance();
        byte[] serialized = serialize(message, CancelDelegationTokenResponseProto.class);

        Object result = ObjectWritable.readObject(new DelegatingDataInput(serialized), null);

        assertThat(result).isEqualTo(message);
    }

    private static byte[] serialize(Object instance, Class<?> declaredClass) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        ObjectWritable.writeObject(out, instance, declaredClass, null);
        out.flush();
        return bytes.toByteArray();
    }

    private static final class DelegatingDataInput implements DataInput {
        private final DataInputStream input;

        private DelegatingDataInput(byte[] data) {
            input = new DataInputStream(new ByteArrayInputStream(data));
        }

        @Override
        public void readFully(byte[] bytes) throws IOException {
            input.readFully(bytes);
        }

        @Override
        public void readFully(byte[] bytes, int offset, int length) throws IOException {
            input.readFully(bytes, offset, length);
        }

        @Override
        public int skipBytes(int count) throws IOException {
            return input.skipBytes(count);
        }

        @Override
        public boolean readBoolean() throws IOException {
            return input.readBoolean();
        }

        @Override
        public byte readByte() throws IOException {
            return input.readByte();
        }

        @Override
        public int readUnsignedByte() throws IOException {
            return input.readUnsignedByte();
        }

        @Override
        public short readShort() throws IOException {
            return input.readShort();
        }

        @Override
        public int readUnsignedShort() throws IOException {
            return input.readUnsignedShort();
        }

        @Override
        public char readChar() throws IOException {
            return input.readChar();
        }

        @Override
        public int readInt() throws IOException {
            return input.readInt();
        }

        @Override
        public long readLong() throws IOException {
            return input.readLong();
        }

        @Override
        public float readFloat() throws IOException {
            return input.readFloat();
        }

        @Override
        public double readDouble() throws IOException {
            return input.readDouble();
        }

        @SuppressWarnings("deprecation")
        @Override
        public String readLine() throws IOException {
            return input.readLine();
        }

        @Override
        public String readUTF() throws IOException {
            return input.readUTF();
        }
    }
}
