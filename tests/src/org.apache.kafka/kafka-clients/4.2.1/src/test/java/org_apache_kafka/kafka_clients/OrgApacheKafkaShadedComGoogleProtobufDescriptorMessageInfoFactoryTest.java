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
import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos.OneofDescriptorProto;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.Descriptor;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.DescriptorValidationException;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.FileDescriptor;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistryLite;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageV3;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageV3.FieldAccessorTable;
import org.apache.kafka.shaded.com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufDescriptorMessageInfoFactoryTest {

    @Test
    void testGeneratedMessageV3SchemaPathUsesDescriptorMessageInfoFactory() throws Exception {
        DescriptorBackedMessage message = new DescriptorBackedMessage();

        message.mergeEmptyPayloadThroughGeneratedMessageV3Schema();

        assertThat(message.getDescriptorForType().getFullName())
                .isEqualTo("forge.kafka.clients.protobuf.DescriptorBackedMessage");
        assertThat(message.getNestedValues(0)).isSameAs(DescriptorBackedMessage.Nested.getDefaultInstance());
        assertThat(message.getSelectedNested()).isSameAs(DescriptorBackedMessage.Nested.getDefaultInstance());
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class DescriptorBackedMessage extends GeneratedMessageV3 {
        private static final Descriptor DESCRIPTOR;
        private static final FieldAccessorTable FIELD_ACCESSOR_TABLE;
        private static final DescriptorBackedMessage DEFAULT_INSTANCE;

        static {
            try {
                FileDescriptor fileDescriptor = FileDescriptor.buildFrom(fileDescriptorProto(), new FileDescriptor[0]);
                DESCRIPTOR = fileDescriptor.findMessageTypeByName("DescriptorBackedMessage");
                FIELD_ACCESSOR_TABLE = new FieldAccessorTable(
                        DESCRIPTOR,
                        new String[] {"ScalarValue", "NestedValues", "SelectedNested", "SelectedText"});
                DEFAULT_INSTANCE = new DescriptorBackedMessage();
            } catch (DescriptorValidationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private int scalarValue_;
        private List<Nested> nestedValues_;
        private int choiceCase_;
        private Object choice_;

        private DescriptorBackedMessage() {
            scalarValue_ = 0;
            nestedValues_ = new ArrayList<>();
            nestedValues_.add(Nested.getDefaultInstance());
            choiceCase_ = 3;
            choice_ = Nested.getDefaultInstance();
        }

        public static DescriptorBackedMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        public Nested getNestedValues(int index) {
            return nestedValues_.get(index);
        }

        public Nested getSelectedNested() {
            if (choiceCase_ == 3) {
                return (Nested) choice_;
            }
            return Nested.getDefaultInstance();
        }

        public String getSelectedText() {
            if (choiceCase_ == 4) {
                return (String) choice_;
            }
            return "";
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
            DescriptorProto nestedMessage = DescriptorProto.newBuilder()
                    .setName("Nested")
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("name")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(FieldDescriptorProto.Type.TYPE_STRING))
                    .build();

            DescriptorProto message = DescriptorProto.newBuilder()
                    .setName("DescriptorBackedMessage")
                    .addNestedType(nestedMessage)
                    .addOneofDecl(OneofDescriptorProto.newBuilder().setName("choice"))
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("scalar_value")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(FieldDescriptorProto.Type.TYPE_INT32))
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("nested_values")
                            .setNumber(2)
                            .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                            .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName(".forge.kafka.clients.protobuf.DescriptorBackedMessage.Nested"))
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("selected_nested")
                            .setNumber(3)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName(".forge.kafka.clients.protobuf.DescriptorBackedMessage.Nested")
                            .setOneofIndex(0))
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("selected_text")
                            .setNumber(4)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(FieldDescriptorProto.Type.TYPE_STRING)
                            .setOneofIndex(0))
                    .build();

            return FileDescriptorProto.newBuilder()
                    .setName("descriptor_message_info_factory_test.proto")
                    .setPackage("forge.kafka.clients.protobuf")
                    .setSyntax("proto3")
                    .addMessageType(message)
                    .build();
        }

        @SuppressWarnings("checkstyle:MemberName")
        public static final class Nested extends GeneratedMessageV3 {
            private static final Descriptor DESCRIPTOR;
            private static final FieldAccessorTable FIELD_ACCESSOR_TABLE;
            private static final Nested DEFAULT_INSTANCE;

            static {
                DESCRIPTOR = DescriptorBackedMessage.DESCRIPTOR.findNestedTypeByName("Nested");
                FIELD_ACCESSOR_TABLE = new FieldAccessorTable(DESCRIPTOR, new String[] {"Name"});
                DEFAULT_INSTANCE = new Nested();
            }

            private Object name_;

            private Nested() {
                name_ = "";
            }

            public static Nested getDefaultInstance() {
                return DEFAULT_INSTANCE;
            }

            public String getName() {
                return (String) name_;
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
        }
    }
}
