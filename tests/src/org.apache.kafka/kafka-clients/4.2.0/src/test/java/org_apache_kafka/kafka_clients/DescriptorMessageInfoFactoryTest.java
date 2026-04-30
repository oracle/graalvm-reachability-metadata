/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Collections;
import java.util.List;

import org.apache.kafka.shaded.com.google.protobuf.CodedInputStream;
import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistryLite;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageV3;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.apache.kafka.shaded.com.google.protobuf.UnknownFieldSet;
import org.junit.jupiter.api.Test;

public class DescriptorMessageInfoFactoryTest {
    @Test
    void buildsDescriptorBackedSchemaForOneofAndRepeatedMessageFields() throws Exception {
        CoverageProbeMessage message = CoverageProbeMessage.getDefaultInstance();
        CodedInputStream input = CodedInputStream.newInstance(new byte[0]);

        message.mergeEmptyInputThroughGeneratedMessageSchema(input);

        assertSame(NestedMessage.getDefaultInstance(), message.getNestedValue());
        assertEquals(0, message.getValuesCount());
    }

    public static final class CoverageProbeMessage extends GeneratedMessageV3 {
        private static final Descriptors.Descriptor DESCRIPTOR = TestProtoDescriptors.messageDescriptor("CoverageProbe");
        private static final CoverageProbeMessage DEFAULT_INSTANCE = new CoverageProbeMessage();
        private static final FieldAccessorTable FIELD_ACCESSOR_TABLE = new FieldAccessorTable(
                DESCRIPTOR,
                new String[] {"NestedValue", "StringValue", "Values", "Kind"});

        @SuppressWarnings("unused")
        private int kindCase_;

        @SuppressWarnings("unused")
        private Object kind_;

        @SuppressWarnings("unused")
        private List<NestedMessage> values_ = Collections.emptyList();

        public static CoverageProbeMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        public NestedMessage getNestedValue() {
            return NestedMessage.getDefaultInstance();
        }

        public String getStringValue() {
            return "";
        }

        public List<NestedMessage> getValuesList() {
            return values_;
        }

        public int getValuesCount() {
            return values_.size();
        }

        public NestedMessage getValues(int index) {
            return values_.get(index);
        }

        void mergeEmptyInputThroughGeneratedMessageSchema(CodedInputStream input) throws Exception {
            mergeFromAndMakeImmutableInternal(input, ExtensionRegistryLite.getEmptyRegistry());
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
            return new CoverageProbeMessage();
        }

        @Override
        public Descriptors.Descriptor getDescriptorForType() {
            return DESCRIPTOR;
        }

        @Override
        public CoverageProbeMessage getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        @Override
        public UnknownFieldSet getUnknownFields() {
            return UnknownFieldSet.getDefaultInstance();
        }
    }

    public static final class NestedMessage extends GeneratedMessageV3 {
        private static final Descriptors.Descriptor DESCRIPTOR = TestProtoDescriptors.messageDescriptor("Nested");
        private static final NestedMessage DEFAULT_INSTANCE = new NestedMessage();
        private static final FieldAccessorTable FIELD_ACCESSOR_TABLE = new FieldAccessorTable(
                DESCRIPTOR,
                new String[] {"Label"});

        @SuppressWarnings("unused")
        private String label_ = "";

        public static NestedMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        public String getLabel() {
            return label_;
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
            return new NestedMessage();
        }

        @Override
        public Descriptors.Descriptor getDescriptorForType() {
            return DESCRIPTOR;
        }

        @Override
        public NestedMessage getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        @Override
        public UnknownFieldSet getUnknownFields() {
            return UnknownFieldSet.getDefaultInstance();
        }
    }

    private static final class TestProtoDescriptors {
        private static final Descriptors.FileDescriptor FILE_DESCRIPTOR = fileDescriptor();

        private static Descriptors.Descriptor messageDescriptor(String name) {
            return FILE_DESCRIPTOR.findMessageTypeByName(name);
        }

        private static Descriptors.FileDescriptor fileDescriptor() {
            DescriptorProtos.DescriptorProto nestedMessage = DescriptorProtos.DescriptorProto.newBuilder()
                    .setName("Nested")
                    .addField(field("label", 1, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                    .build();
            DescriptorProtos.OneofDescriptorProto kindOneof = DescriptorProtos.OneofDescriptorProto.newBuilder()
                    .setName("kind")
                    .build();
            DescriptorProtos.DescriptorProto coverageProbeMessage = DescriptorProtos.DescriptorProto.newBuilder()
                    .setName("CoverageProbe")
                    .addOneofDecl(kindOneof)
                    .addField(messageField("nested_value", 1, ".coverage.Nested", 0))
                    .addField(field("string_value", 2, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING).toBuilder()
                            .setOneofIndex(0))
                    .addField(repeatedMessageField("values", 3, ".coverage.Nested"))
                    .build();
            DescriptorProtos.FileDescriptorProto fileDescriptorProto = DescriptorProtos.FileDescriptorProto.newBuilder()
                    .setName("coverage_probe.proto")
                    .setPackage("coverage")
                    .setSyntax("proto3")
                    .addMessageType(nestedMessage)
                    .addMessageType(coverageProbeMessage)
                    .build();
            try {
                return Descriptors.FileDescriptor.buildFrom(fileDescriptorProto, new Descriptors.FileDescriptor[0]);
            } catch (Descriptors.DescriptorValidationException exception) {
                throw new IllegalStateException("Unable to build test descriptor", exception);
            }
        }

        private static DescriptorProtos.FieldDescriptorProto field(
                String name,
                int number,
                DescriptorProtos.FieldDescriptorProto.Type type) {
            return DescriptorProtos.FieldDescriptorProto.newBuilder()
                    .setName(name)
                    .setNumber(number)
                    .setType(type)
                    .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build();
        }

        private static DescriptorProtos.FieldDescriptorProto messageField(
                String name,
                int number,
                String typeName,
                int oneofIndex) {
            return DescriptorProtos.FieldDescriptorProto.newBuilder()
                    .setName(name)
                    .setNumber(number)
                    .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                    .setTypeName(typeName)
                    .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .setOneofIndex(oneofIndex)
                    .build();
        }

        private static DescriptorProtos.FieldDescriptorProto repeatedMessageField(
                String name,
                int number,
                String typeName) {
            return DescriptorProtos.FieldDescriptorProto.newBuilder()
                    .setName(name)
                    .setNumber(number)
                    .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                    .setTypeName(typeName)
                    .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
                    .build();
        }
    }
}
