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

public class ExtensionSchemasTest {

    @Test
    void schemaInitializationLoadsTheFullRuntimeExtensionSchemaForProto2Messages() {
        assertThatNoException().isThrownBy(() -> new Proto2ParentMessage().initializeSchemaFromEmptyInput());
    }

    private static final class Proto2Descriptors {
        private static final Descriptors.Descriptor PROTO2_CHILD_DESCRIPTOR;
        private static final Descriptors.Descriptor PROTO2_PARENT_DESCRIPTOR;

        static {
            try {
                DescriptorProtos.DescriptorProto proto2Child = DescriptorProtos.DescriptorProto.newBuilder()
                    .setName("Proto2Child")
                    .build();
                DescriptorProtos.DescriptorProto proto2Parent = DescriptorProtos.DescriptorProto.newBuilder()
                    .setName("Proto2Parent")
                    .addField(
                        DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName("children")
                            .setNumber(1)
                            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
                            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName(".extension.schemas.test.Proto2Child")
                            .build()
                    )
                    .build();

                Descriptors.FileDescriptor fileDescriptor = Descriptors.FileDescriptor.buildFrom(
                    DescriptorProtos.FileDescriptorProto.newBuilder()
                        .setName("extension_schemas_test.proto")
                        .setPackage("extension.schemas.test")
                        .setSyntax("proto2")
                        .addMessageType(proto2Child)
                        .addMessageType(proto2Parent)
                        .build(),
                    new Descriptors.FileDescriptor[0]
                );

                PROTO2_CHILD_DESCRIPTOR = fileDescriptor.findMessageTypeByName("Proto2Child");
                PROTO2_PARENT_DESCRIPTOR = fileDescriptor.findMessageTypeByName("Proto2Parent");
            } catch (Descriptors.DescriptorValidationException e) {
                throw new IllegalStateException(e);
            }
        }

        private Proto2Descriptors() {
        }
    }

    public static final class Proto2ChildMessage extends GeneratedMessageV3 {
        private static final Proto2ChildMessage DEFAULT_INSTANCE = new Proto2ChildMessage();

        public static Proto2ChildMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        @Override
        public Proto2ChildMessage getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        @Override
        public Descriptors.Descriptor getDescriptorForType() {
            return Proto2Descriptors.PROTO2_CHILD_DESCRIPTOR;
        }

        @Override
        protected GeneratedMessageV3.FieldAccessorTable internalGetFieldAccessorTable() {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        protected Message.Builder newBuilderForType(GeneratedMessageV3.BuilderParent parent) {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        public Message.Builder newBuilderForType() {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        public Message.Builder toBuilder() {
            throw new UnsupportedOperationException("Not needed for this test");
        }
    }

    public static final class Proto2ParentMessage extends GeneratedMessageV3 {
        private static final Proto2ParentMessage DEFAULT_INSTANCE = new Proto2ParentMessage();

        private List<Proto2ChildMessage> children_;

        public Proto2ParentMessage() {
            this.children_ = new ArrayList<>();
        }

        public static Proto2ParentMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        @Override
        public Proto2ParentMessage getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        public Proto2ChildMessage getChildren(int index) {
            return this.children_.get(index);
        }

        public void initializeSchemaFromEmptyInput() throws InvalidProtocolBufferException {
            mergeFromAndMakeImmutableInternal(
                CodedInputStream.newInstance(new byte[0]),
                ExtensionRegistryLite.getEmptyRegistry()
            );
        }

        @Override
        public Descriptors.Descriptor getDescriptorForType() {
            return Proto2Descriptors.PROTO2_PARENT_DESCRIPTOR;
        }

        @Override
        protected GeneratedMessageV3.FieldAccessorTable internalGetFieldAccessorTable() {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        protected Message.Builder newBuilderForType(GeneratedMessageV3.BuilderParent parent) {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        public Message.Builder newBuilderForType() {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        public Message.Builder toBuilder() {
            throw new UnsupportedOperationException("Not needed for this test");
        }
    }
}
