/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.CodedInputStream;
import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos.DescriptorProto;
import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.Descriptor;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.DescriptorValidationException;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.FileDescriptor;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistryLite;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageV3;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageV3.FieldAccessorTable;
import org.apache.kafka.shaded.com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufExtensionSchemasTest {

    @Test
    void fullRuntimeSchemaIsLoadedWhenGeneratedMessageV3ParsesProto2Fields() throws Exception {
        FullRuntimeMessage message = FullRuntimeMessage.parseFrom(new byte[] {8, 42});

        assertThat(message.hasScalarValue()).isTrue();
        assertThat(message.getScalarValue()).isEqualTo(42);
    }

    @SuppressWarnings({"checkstyle:MemberName", "deprecation"})
    public static final class FullRuntimeMessage extends GeneratedMessageV3 {
        private static final Descriptor DESCRIPTOR;
        private static final FieldAccessorTable FIELD_ACCESSOR_TABLE;
        private static final FullRuntimeMessage DEFAULT_INSTANCE = new FullRuntimeMessage();
        private static final int SCALAR_VALUE_PRESENT = 0x00000001;

        private int bitField0_;
        private int scalarValue_;

        static {
            try {
                FileDescriptor fileDescriptor = FileDescriptor.buildFrom(fileDescriptorProto(), new FileDescriptor[0]);
                DESCRIPTOR = fileDescriptor.findMessageTypeByName("FullRuntimeMessage");
                FIELD_ACCESSOR_TABLE = new FieldAccessorTable(
                        DESCRIPTOR,
                        new String[] {"ScalarValue"},
                        FullRuntimeMessage.class,
                        Builder.class);
            } catch (DescriptorValidationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private FullRuntimeMessage() {
        }

        public static FullRuntimeMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        public static FullRuntimeMessage parseFrom(byte[] data) throws InvalidProtocolBufferException {
            FullRuntimeMessage message = new FullRuntimeMessage();
            message.mergeFromAndMakeImmutableInternal(
                    CodedInputStream.newInstance(data),
                    ExtensionRegistryLite.getEmptyRegistry());
            return message;
        }

        public int getScalarValue() {
            return scalarValue_;
        }

        public boolean hasScalarValue() {
            return (bitField0_ & SCALAR_VALUE_PRESENT) != 0;
        }

        @Override
        protected FieldAccessorTable internalGetFieldAccessorTable() {
            return FIELD_ACCESSOR_TABLE;
        }

        @Override
        public Builder newBuilderForType() {
            return new Builder();
        }

        @Override
        protected Builder newBuilderForType(BuilderParent parent) {
            return new Builder(parent);
        }

        @Override
        public Builder toBuilder() {
            return newBuilderForType();
        }

        @Override
        public Message getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        private static FileDescriptorProto fileDescriptorProto() {
            DescriptorProto message = DescriptorProto.newBuilder()
                    .setName("FullRuntimeMessage")
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("scalar_value")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(FieldDescriptorProto.Type.TYPE_INT32))
                    .build();

            return FileDescriptorProto.newBuilder()
                    .setName("extension_schemas_full_runtime_test.proto")
                    .setPackage("forge.kafka.clients.protobuf.extension_schemas")
                    .setSyntax("proto2")
                    .addMessageType(message)
                    .build();
        }

        public static final class Builder extends GeneratedMessageV3.Builder<Builder> {
            private int scalarValue;
            private boolean hasScalarValue;

            private Builder() {
            }

            private Builder(BuilderParent parent) {
                super(parent);
            }

            public int getScalarValue() {
                return scalarValue;
            }

            public Builder setScalarValue(int scalarValue) {
                this.scalarValue = scalarValue;
                hasScalarValue = true;
                return this;
            }

            public boolean hasScalarValue() {
                return hasScalarValue;
            }

            public Builder clearScalarValue() {
                scalarValue = 0;
                hasScalarValue = false;
                return this;
            }

            @Override
            protected FieldAccessorTable internalGetFieldAccessorTable() {
                return FIELD_ACCESSOR_TABLE;
            }

            @Override
            public Builder clone() {
                Builder builder = new Builder();
                builder.scalarValue = scalarValue;
                builder.hasScalarValue = hasScalarValue;
                return builder;
            }

            @Override
            public FullRuntimeMessage build() {
                return buildPartial();
            }

            @Override
            public FullRuntimeMessage buildPartial() {
                FullRuntimeMessage message = new FullRuntimeMessage();
                message.scalarValue_ = scalarValue;
                if (hasScalarValue) {
                    message.bitField0_ |= SCALAR_VALUE_PRESENT;
                }
                return message;
            }

            @Override
            public Message getDefaultInstanceForType() {
                return DEFAULT_INSTANCE;
            }
        }
    }
}
