/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class StandardBiMapInnerInverseTest {
    private static final String INVERSE_CLASS_NAME = "com.google.common.collect.StandardBiMap$Inverse";
    private static final String FORWARD_CLASS_NAME = "com.google.common.collect.StandardBiMap";
    private static final int BASE_WIRE_HANDLE = 0x7E0000;
    private static final int INVERSE_OBJECT_HANDLE = BASE_WIRE_HANDLE + 2;

    @Test
    void inverseSerializationWritesAndReadsForwardBiMapReference() throws Exception {
        Object inverse = deserialize(serializedSelfReferentialInverse());

        assertThat(inverse.getClass().getName()).isEqualTo(INVERSE_CLASS_NAME);

        byte[] serialized = serialize(inverse);
        Object restored = deserialize(serialized);

        assertThat(restored.getClass().getName()).isEqualTo(INVERSE_CLASS_NAME);
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return input.readObject();
        }
    }

    private static byte[] serializedSelfReferentialInverse() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream stream = new DataOutputStream(bytes);

        stream.writeShort(0xACED);
        stream.writeShort(5);
        stream.writeByte(0x73); // TC_OBJECT
        writeInverseClassDescriptor(stream);
        writeHandle(stream, INVERSE_OBJECT_HANDLE);
        stream.writeByte(0x78); // TC_ENDBLOCKDATA

        return bytes.toByteArray();
    }

    private static void writeInverseClassDescriptor(DataOutputStream stream) throws IOException {
        stream.writeByte(0x72); // TC_CLASSDESC
        stream.writeUTF(INVERSE_CLASS_NAME);
        stream.writeLong(0L);
        stream.writeByte(0x03); // SC_SERIALIZABLE | SC_WRITE_METHOD
        stream.writeShort(0);
        stream.writeByte(0x78); // TC_ENDBLOCKDATA
        writeForwardClassDescriptor(stream);
    }

    private static void writeForwardClassDescriptor(DataOutputStream stream) throws IOException {
        stream.writeByte(0x72); // TC_CLASSDESC
        stream.writeUTF(FORWARD_CLASS_NAME);
        stream.writeLong(0L);
        stream.writeByte(0x02); // SC_SERIALIZABLE
        stream.writeShort(0);
        stream.writeByte(0x78); // TC_ENDBLOCKDATA
        stream.writeByte(0x70); // TC_NULL
    }

    private static void writeHandle(DataOutputStream stream, int handle) throws IOException {
        stream.writeByte(0x71); // TC_REFERENCE
        stream.writeInt(handle);
    }
}
