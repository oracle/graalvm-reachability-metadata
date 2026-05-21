/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos.DescriptorProto;
import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.Descriptor;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.DescriptorValidationException;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.FileDescriptor;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageV3;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageV3.FieldAccessorTable;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.MetricsData;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufGeneratedMessageLiteInnerSerializedFormTest {

    @Test
    void testSerializedFormRestoresMessageFromDefaultInstanceField() throws Exception {
        Object roundTripped = deserialize(serialize(MetricsData.getDefaultInstance()));

        assertThat(roundTripped).isEqualTo(MetricsData.getDefaultInstance());
    }

    @Test
    void testSerializedFormResolvesClassNameAndRestoresMessageFromLegacyDefaultInstanceField() throws Exception {
        Object roundTripped = deserialize(serializeWithoutMessageClass(
                LegacyDefaultInstanceMessage.defaultInstance,
                LegacyDefaultInstanceMessage.class));

        assertThat(roundTripped).isSameAs(LegacyDefaultInstanceMessage.defaultInstance);
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(value);
        }
        return output.toByteArray();
    }

    private static byte[] serializeWithoutMessageClass(Object value, Class<?> messageClass) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new MessageClassNullingObjectOutputStream(output, messageClass)) {
            objectOutput.writeObject(value);
        }
        return output.toByteArray();
    }

    private static Object deserialize(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInput = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return objectInput.readObject();
        }
    }

    private static final class MessageClassNullingObjectOutputStream extends ObjectOutputStream {
        private final Class<?> messageClass;

        MessageClassNullingObjectOutputStream(ByteArrayOutputStream output, Class<?> messageClass) throws IOException {
            super(output);
            this.messageClass = messageClass;
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (object == messageClass) {
                return null;
            }
            return super.replaceObject(object);
        }
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class LegacyDefaultInstanceMessage extends GeneratedMessageV3 {
        private static final Descriptor DESCRIPTOR;
        private static final FieldAccessorTable FIELD_ACCESSOR_TABLE;
        public static final LegacyDefaultInstanceMessage defaultInstance = new LegacyDefaultInstanceMessage();

        static {
            try {
                FileDescriptor fileDescriptor = FileDescriptor.buildFrom(fileDescriptorProto(), new FileDescriptor[0]);
                DESCRIPTOR = fileDescriptor.findMessageTypeByName("LegacyDefaultInstanceMessage");
                FIELD_ACCESSOR_TABLE = new FieldAccessorTable(DESCRIPTOR, new String[0]);
            } catch (DescriptorValidationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private LegacyDefaultInstanceMessage() {
        }

        @Override
        protected FieldAccessorTable internalGetFieldAccessorTable() {
            return FIELD_ACCESSOR_TABLE;
        }

        @Override
        public Message.Builder newBuilderForType() {
            return new Builder();
        }

        @Override
        protected Message.Builder newBuilderForType(BuilderParent parent) {
            return new Builder(parent);
        }

        @Override
        public Message.Builder toBuilder() {
            return new Builder();
        }

        @Override
        public Message getDefaultInstanceForType() {
            return defaultInstance;
        }

        @Override
        public Descriptor getDescriptorForType() {
            return DESCRIPTOR;
        }

        private static FileDescriptorProto fileDescriptorProto() {
            return FileDescriptorProto.newBuilder()
                    .setName("legacy_default_instance_message.proto")
                    .setPackage("forge.kafka.clients.protobuf")
                    .setSyntax("proto3")
                    .addMessageType(DescriptorProto.newBuilder().setName("LegacyDefaultInstanceMessage"))
                    .build();
        }

        public static final class Builder extends GeneratedMessageV3.Builder<Builder> {
            private Builder() {
            }

            private Builder(BuilderParent parent) {
                super(parent);
            }

            @Override
            protected FieldAccessorTable internalGetFieldAccessorTable() {
                return FIELD_ACCESSOR_TABLE;
            }

            @Override
            public LegacyDefaultInstanceMessage build() {
                return defaultInstance;
            }

            @Override
            public LegacyDefaultInstanceMessage buildPartial() {
                return defaultInstance;
            }

            @Override
            public Builder clone() {
                return new Builder();
            }

            @Override
            public LegacyDefaultInstanceMessage getDefaultInstanceForType() {
                return defaultInstance;
            }
        }
    }
}
