/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.CodedInputStream;
import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistryLite;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageV3;
import org.apache.kafka.shaded.com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.apache.kafka.shaded.com.google.protobuf.Parser;
import org.apache.kafka.shaded.com.google.protobuf.UnknownFieldSet;
import org.apache.kafka.shaded.io.opentelemetry.proto.common.v1.AnyValue;
import org.apache.kafka.shaded.io.opentelemetry.proto.common.v1.ArrayValue;
import org.apache.kafka.shaded.io.opentelemetry.proto.common.v1.KeyValue;
import org.apache.kafka.shaded.io.opentelemetry.proto.common.v1.KeyValueList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufDescriptorMessageInfoFactoryTest {

    @Test
    @Timeout(30)
    void parsesGeneratedMessageWithMessageOneofAndRepeatedMessageFields() throws Exception {
        AnyValue nestedString = AnyValue.newBuilder()
                .setStringValue("nested")
                .build();
        AnyValue nestedKvList = AnyValue.newBuilder()
                .setKvlistValue(KeyValueList.newBuilder()
                        .addValues(KeyValue.newBuilder()
                                .setKey("attribute")
                                .setValue(nestedString)
                                .build())
                        .build())
                .build();
        AnyValue message = AnyValue.newBuilder()
                .setArrayValue(ArrayValue.newBuilder()
                        .addValues(nestedString)
                        .addValues(nestedKvList)
                        .build())
                .build();

        AnyValue parsed = AnyValue.parseFrom(message.toByteArray());

        assertThat(parsed.getValueCase()).isEqualTo(AnyValue.ValueCase.ARRAY_VALUE);
        assertThat(parsed.getArrayValue().getValuesList())
                .extracting(AnyValue::getValueCase)
                .containsExactly(AnyValue.ValueCase.STRING_VALUE, AnyValue.ValueCase.KVLIST_VALUE);
        assertThat(parsed.getArrayValue().getValues(1).getKvlistValue().getValues(0).getKey())
                .isEqualTo("attribute");
    }

    @Test
    @Timeout(30)
    void generatedMessageV3SchemaInspectsDescriptorBackedMessageShape() throws Exception {
        DescriptorBackedMessage message = DescriptorBackedMessage.newMutableInstance();

        message.mergeEmptyInputThroughGeneratedMessageV3();

        assertThat(message.getEntriesList()).isEmpty();
        assertThat(message.getChild()).isSameAs(ChildMessage.getDefaultInstance());
    }

    public static final class DescriptorBackedMessage extends GeneratedMessageV3 {
        private static final DescriptorBackedMessage DEFAULT_INSTANCE = new DescriptorBackedMessage();
        private static final Descriptors.Descriptor DESCRIPTOR;

        private List<ChildMessage> entries_ = Collections.emptyList();
        private int kindCase_;
        private Object kind_;

        static {
            Descriptors.FileDescriptor fileDescriptor = buildFileDescriptor();
            DESCRIPTOR = fileDescriptor.findMessageTypeByName("DescriptorBackedMessage");
        }

        public static DescriptorBackedMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        static DescriptorBackedMessage newMutableInstance() {
            return new DescriptorBackedMessage();
        }

        void mergeEmptyInputThroughGeneratedMessageV3() throws InvalidProtocolBufferException {
            mergeFromAndMakeImmutableInternal(
                    CodedInputStream.newInstance(new byte[0]),
                    ExtensionRegistryLite.getEmptyRegistry());
        }

        public List<ChildMessage> getEntriesList() {
            return entries_;
        }

        public ChildMessage getEntries(int index) {
            return entries_.get(index);
        }

        public ChildMessage getChild() {
            if (kindCase_ == 2) {
                return (ChildMessage) kind_;
            }
            return ChildMessage.getDefaultInstance();
        }

        @Override
        public Descriptors.Descriptor getDescriptorForType() {
            return DESCRIPTOR;
        }

        @Override
        public DescriptorBackedMessage getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        @Override
        protected GeneratedMessageV3.FieldAccessorTable internalGetFieldAccessorTable() {
            throw new UnsupportedOperationException("FieldAccessorTable is not needed for schema inspection");
        }

        @Override
        protected Message.Builder newBuilderForType(GeneratedMessageV3.BuilderParent parent) {
            throw new UnsupportedOperationException("Builder is not needed for schema inspection");
        }

        @Override
        public Message.Builder newBuilderForType() {
            throw new UnsupportedOperationException("Builder is not needed for schema inspection");
        }

        @Override
        public Message.Builder toBuilder() {
            throw new UnsupportedOperationException("Builder is not needed for schema inspection");
        }

        @Override
        public Parser<? extends GeneratedMessageV3> getParserForType() {
            throw new UnsupportedOperationException("Parser is not needed for schema inspection");
        }

        @Override
        public UnknownFieldSet getUnknownFields() {
            return UnknownFieldSet.getDefaultInstance();
        }
    }

    public static final class ChildMessage extends GeneratedMessageV3 {
        private static final ChildMessage DEFAULT_INSTANCE = new ChildMessage();
        private String name_ = "";

        public static ChildMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        public String getName() {
            return name_;
        }

        @Override
        public Descriptors.Descriptor getDescriptorForType() {
            return DescriptorBackedMessage.getDefaultInstance()
                    .getDescriptorForType()
                    .getFile()
                    .findMessageTypeByName("ChildMessage");
        }

        @Override
        public ChildMessage getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        @Override
        protected GeneratedMessageV3.FieldAccessorTable internalGetFieldAccessorTable() {
            throw new UnsupportedOperationException("FieldAccessorTable is not needed for schema inspection");
        }

        @Override
        protected Message.Builder newBuilderForType(GeneratedMessageV3.BuilderParent parent) {
            throw new UnsupportedOperationException("Builder is not needed for schema inspection");
        }

        @Override
        public Message.Builder newBuilderForType() {
            throw new UnsupportedOperationException("Builder is not needed for schema inspection");
        }

        @Override
        public Message.Builder toBuilder() {
            throw new UnsupportedOperationException("Builder is not needed for schema inspection");
        }

        public Parser<? extends GeneratedMessageV3> getParserForType() {
            throw new UnsupportedOperationException("Parser is not needed for schema inspection");
        }

        @Override
        public UnknownFieldSet getUnknownFields() {
            return UnknownFieldSet.getDefaultInstance();
        }
    }

    private static Descriptors.FileDescriptor buildFileDescriptor() {
        DescriptorProtos.DescriptorProto childMessage = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("ChildMessage")
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("name")
                        .setNumber(1)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                        .build())
                .build();
        DescriptorProtos.DescriptorProto descriptorBackedMessage = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("DescriptorBackedMessage")
                .addOneofDecl(DescriptorProtos.OneofDescriptorProto.newBuilder()
                        .setName("kind")
                        .build())
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("entries")
                        .setNumber(1)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName(".kafka.clients.coverage.ChildMessage")
                        .build())
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("child")
                        .setNumber(2)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName(".kafka.clients.coverage.ChildMessage")
                        .setOneofIndex(0)
                        .build())
                .build();
        DescriptorProtos.FileDescriptorProto file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("descriptor_message_info_factory_test.proto")
                .setPackage("kafka.clients.coverage")
                .setSyntax("proto3")
                .addMessageType(descriptorBackedMessage)
                .addMessageType(childMessage)
                .build();
        try {
            return Descriptors.FileDescriptor.buildFrom(file, new Descriptors.FileDescriptor[0]);
        } catch (Descriptors.DescriptorValidationException e) {
            throw new IllegalStateException("Invalid test protobuf descriptor", e);
        }
    }
}
