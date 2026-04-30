/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.apache.kafka.shaded.com.google.protobuf.CodedInputStream;
import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistry;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageV3;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.apache.kafka.shaded.com.google.protobuf.UnknownFieldSet;
import org.junit.jupiter.api.Test;

public class ExtensionSchemasTest {
    @Test
    void loadsFullRuntimeExtensionSchemaForProto2GeneratedMessage() throws Exception {
        Proto2Message message = Proto2Message.getDefaultInstance();
        CodedInputStream input = CodedInputStream.newInstance(new byte[0]);

        message.mergeEmptyInputThroughGeneratedMessageSchema(input);

        assertSame(Proto2Message.getDefaultInstance(), message.getDefaultInstanceForType());
    }

    public static final class Proto2Message extends GeneratedMessageV3 {
        private static final Descriptors.Descriptor DESCRIPTOR = TestProtoDescriptors.messageDescriptor();
        private static final Proto2Message DEFAULT_INSTANCE = new Proto2Message();
        private static final FieldAccessorTable FIELD_ACCESSOR_TABLE = new FieldAccessorTable(
                DESCRIPTOR,
                new String[0]);

        public static Proto2Message getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        void mergeEmptyInputThroughGeneratedMessageSchema(CodedInputStream input) throws Exception {
            mergeFromAndMakeImmutableInternal(input, ExtensionRegistry.getEmptyRegistry());
        }

        @Override
        protected FieldAccessorTable internalGetFieldAccessorTable() {
            return FIELD_ACCESSOR_TABLE;
        }

        @Override
        public Message.Builder newBuilderForType() {
            throw new UnsupportedOperationException("Builder is not needed for schema creation.");
        }

        @Override
        public Message.Builder toBuilder() {
            throw new UnsupportedOperationException("Builder is not needed for schema creation.");
        }

        @Override
        protected Message.Builder newBuilderForType(BuilderParent parent) {
            throw new UnsupportedOperationException("Builder is not needed for schema creation.");
        }

        @Override
        protected Object newInstance(UnusedPrivateParameter unused) {
            return new Proto2Message();
        }

        @Override
        public Descriptors.Descriptor getDescriptorForType() {
            return DESCRIPTOR;
        }

        @Override
        public Proto2Message getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        @Override
        public UnknownFieldSet getUnknownFields() {
            return UnknownFieldSet.getDefaultInstance();
        }
    }

    private static final class TestProtoDescriptors {
        private static final Descriptors.FileDescriptor FILE_DESCRIPTOR = fileDescriptor();

        private static Descriptors.Descriptor messageDescriptor() {
            return FILE_DESCRIPTOR.findMessageTypeByName("Proto2Message");
        }

        private static Descriptors.FileDescriptor fileDescriptor() {
            DescriptorProtos.DescriptorProto message = DescriptorProtos.DescriptorProto.newBuilder()
                    .setName("Proto2Message")
                    .build();
            DescriptorProtos.FileDescriptorProto fileDescriptorProto = DescriptorProtos.FileDescriptorProto.newBuilder()
                    .setName("extension_schemas_probe.proto")
                    .setPackage("coverage")
                    .setSyntax("proto2")
                    .addMessageType(message)
                    .build();
            try {
                return Descriptors.FileDescriptor.buildFrom(fileDescriptorProto, new Descriptors.FileDescriptor[0]);
            } catch (Descriptors.DescriptorValidationException exception) {
                throw new IllegalStateException("Unable to build test descriptor", exception);
            }
        }
    }
}
