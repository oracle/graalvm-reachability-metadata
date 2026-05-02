/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_java;

import com.google.protobuf.DescriptorProtos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GeneratedMessageLiteInnerSerializedFormTest {
    @Test
    void deserializesGeneratedMessageThroughSerializedFormReadResolve() throws Exception {
        DescriptorProtos.DescriptorProto messageType = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Person")
                .build();
        DescriptorProtos.FileDescriptorProto original = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("sample.proto")
                .setPackage("sample")
                .addDependency("common.proto")
                .addMessageType(messageType)
                .build();

        Object restored = deserialize(serialize(original));

        assertThat(restored).isInstanceOf(DescriptorProtos.FileDescriptorProto.class);
        assertThat(restored).isNotSameAs(original).isEqualTo(original);
        assertThat(((DescriptorProtos.FileDescriptorProto) restored).toByteArray()).isEqualTo(original.toByteArray());
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return input.readObject();
        }
    }
}
