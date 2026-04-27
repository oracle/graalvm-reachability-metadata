/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.shaded.com.google.protobuf.CodedInputStream;
import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistryLite;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageV3;
import org.apache.kafka.shaded.com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.junit.jupiter.api.Test;

public class DescriptorMessageInfoFactoryTest {

    @Test
    void schemaInitializationUsesDescriptorBasedReflectionForDefaultInstanceOneofAndRepeatedFields() {
        assertThatNoException().isThrownBy(() -> new HelperParentMessage().initializeSchemaFromEmptyInput());
    }

    private static final class HelperDescriptors {
        private static final Descriptors.Descriptor HELPER_CHILD_DESCRIPTOR;
        private static final Descriptors.Descriptor HELPER_PARENT_DESCRIPTOR;

        static {
            try {
                DescriptorProtos.DescriptorProto helperChild = DescriptorProtos.DescriptorProto.newBuilder()
                    .setName("HelperChild")
                    .build();
                DescriptorProtos.DescriptorProto helperParent = DescriptorProtos.DescriptorProto.newBuilder()
                    .setName("HelperParent")
                    .addOneofDecl(DescriptorProtos.OneofDescriptorProto.newBuilder().setName("choice").build())
                    .addField(
                        DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName("choice_message")
                            .setNumber(1)
                            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName(".descriptor.test.HelperChild")
                            .setOneofIndex(0)
                            .build()
                    )
                    .addField(
                        DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName("entries")
                            .setNumber(2)
                            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
                            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName(".descriptor.test.HelperChild")
                            .build()
                    )
                    .build();

                Descriptors.FileDescriptor fileDescriptor = Descriptors.FileDescriptor.buildFrom(
                    DescriptorProtos.FileDescriptorProto.newBuilder()
                        .setName("descriptor_message_info_factory_test.proto")
                        .setPackage("descriptor.test")
                        .setSyntax("proto3")
                        .addMessageType(helperChild)
                        .addMessageType(helperParent)
                        .build(),
                    new Descriptors.FileDescriptor[0]
                );

                HELPER_CHILD_DESCRIPTOR = fileDescriptor.findMessageTypeByName("HelperChild");
                HELPER_PARENT_DESCRIPTOR = fileDescriptor.findMessageTypeByName("HelperParent");
            } catch (Descriptors.DescriptorValidationException e) {
                throw new IllegalStateException(e);
            }
        }

        private HelperDescriptors() {
        }
    }

    public static final class HelperChildMessage extends GeneratedMessageV3 {
        private static final HelperChildMessage DEFAULT_INSTANCE = new HelperChildMessage();

        public static HelperChildMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        @Override
        public Descriptors.Descriptor getDescriptorForType() {
            return HelperDescriptors.HELPER_CHILD_DESCRIPTOR;
        }

        @Override
        protected GeneratedMessageV3.FieldAccessorTable internalGetFieldAccessorTable() {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        protected Message.Builder newBuilderForType(GeneratedMessageV3.BuilderParent parent) {
            throw new UnsupportedOperationException("Not needed for this test");
        }
    }

    public static final class HelperParentMessage extends GeneratedMessageV3 {
        private static final HelperParentMessage DEFAULT_INSTANCE = new HelperParentMessage();

        private int choiceCase_;
        private Object choice_;
        private List<HelperChildMessage> entries_;

        public HelperParentMessage() {
            this.choiceCase_ = 0;
            this.choice_ = null;
            this.entries_ = new ArrayList<>();
        }

        public static HelperParentMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        public HelperChildMessage getChoiceMessage() {
            if (this.choiceCase_ == 1) {
                return (HelperChildMessage) this.choice_;
            }
            return HelperChildMessage.getDefaultInstance();
        }

        public HelperChildMessage getEntries(int index) {
            return this.entries_.get(index);
        }

        public void initializeSchemaFromEmptyInput() throws InvalidProtocolBufferException {
            mergeFromAndMakeImmutableInternal(
                CodedInputStream.newInstance(new byte[0]),
                ExtensionRegistryLite.getEmptyRegistry()
            );
        }

        @Override
        public Descriptors.Descriptor getDescriptorForType() {
            return HelperDescriptors.HELPER_PARENT_DESCRIPTOR;
        }

        @Override
        protected GeneratedMessageV3.FieldAccessorTable internalGetFieldAccessorTable() {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        protected Message.Builder newBuilderForType(GeneratedMessageV3.BuilderParent parent) {
            throw new UnsupportedOperationException("Not needed for this test");
        }
    }
}
