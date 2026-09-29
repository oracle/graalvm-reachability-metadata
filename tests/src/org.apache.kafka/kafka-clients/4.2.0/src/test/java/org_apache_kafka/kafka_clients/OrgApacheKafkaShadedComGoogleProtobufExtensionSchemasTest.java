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
    void generatedMessageV3Proto2SchemaUsesFullExtensionSchema() throws Exception {
        Proto2Message message = new Proto2Message();

        message.mergeEmptyPayloadThroughGeneratedMessageV3Schema();

        assertThat(message.getDescriptorForType().getFullName())
                .isEqualTo("forge.kafka.clients.protobuf.Proto2Message");
        assertThat(message.getValue()).isZero();
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class Proto2Message extends GeneratedMessageV3 {
        private static final Descriptor DESCRIPTOR;
        private static final FieldAccessorTable FIELD_ACCESSOR_TABLE;
        private static final Proto2Message DEFAULT_INSTANCE;

        static {
            try {
                FileDescriptor fileDescriptor = FileDescriptor.buildFrom(fileDescriptorProto(), new FileDescriptor[0]);
                DESCRIPTOR = fileDescriptor.findMessageTypeByName("Proto2Message");
                FIELD_ACCESSOR_TABLE = new FieldAccessorTable(DESCRIPTOR, new String[] {"Value"});
                DEFAULT_INSTANCE = new Proto2Message();
            } catch (DescriptorValidationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private int bitField0_;
        private int value_;

        private Proto2Message() {
            bitField0_ = 0;
            value_ = 0;
        }

        public static Proto2Message getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        public int getValue() {
            return value_;
        }

        void mergeEmptyPayloadThroughGeneratedMessageV3Schema() throws InvalidProtocolBufferException {
            CodedInputStream input = CodedInputStream.newInstance(new byte[0]);
            mergeFromAndMakeImmutableInternal(input, ExtensionRegistryLite.getEmptyRegistry());
        }

        @Override
        protected FieldAccessorTable internalGetFieldAccessorTable() {
            return FIELD_ACCESSOR_TABLE;
        }

        @Override
        public Message.Builder newBuilderForType() {
            throw new UnsupportedOperationException("The test only needs schema construction");
        }

        @Override
        protected Message.Builder newBuilderForType(BuilderParent parent) {
            throw new UnsupportedOperationException("The test only needs schema construction");
        }

        @Override
        public Message.Builder toBuilder() {
            throw new UnsupportedOperationException("The test only needs schema construction");
        }

        @Override
        public Message getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        private static FileDescriptorProto fileDescriptorProto() {
            DescriptorProto message = DescriptorProto.newBuilder()
                    .setName("Proto2Message")
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("value")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(FieldDescriptorProto.Type.TYPE_INT32))
                    .build();

            return FileDescriptorProto.newBuilder()
                    .setName("extension_schemas_test.proto")
                    .setPackage("forge.kafka.clients.protobuf")
                    .addMessageType(message)
                    .build();
        }
    }
}
