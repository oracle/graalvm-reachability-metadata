/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.apache.kafka.shaded.com.google.protobuf.CodedInputStream;
import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistryLite;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageV3;
import org.apache.kafka.shaded.com.google.protobuf.MapEntry;
import org.apache.kafka.shaded.com.google.protobuf.MapField;
import org.apache.kafka.shaded.com.google.protobuf.MapFieldReflectionAccessor;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.apache.kafka.shaded.com.google.protobuf.UnknownFieldSet;
import org.apache.kafka.shaded.com.google.protobuf.WireFormat;
import org.junit.jupiter.api.Test;

public class SchemaUtilTest {
    @Test
    void buildsGeneratedMessageSchemaForMapField() throws Exception {
        MapBackedMessage message = MapBackedMessage.getDefaultInstance();
        CodedInputStream input = CodedInputStream.newInstance(new byte[0]);

        message.mergeEmptyInputThroughGeneratedMessageSchema(input);

        assertEquals(0, message.getAttributesCount());
        assertTrue(message.getAttributesMap().isEmpty());
    }

    public static final class MapBackedMessage extends GeneratedMessageV3 {
        private static final Descriptors.Descriptor DESCRIPTOR = TestProtoDescriptors.messageDescriptor();
        private static final MapBackedMessage DEFAULT_INSTANCE = new MapBackedMessage();
        private static final FieldAccessorTable FIELD_ACCESSOR_TABLE = new FieldAccessorTable(
                DESCRIPTOR,
                new String[] {"Attributes"});

        private MapField<String, String> attributes_;

        public static MapBackedMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        public int getAttributesCount() {
            return internalGetAttributes().getMap().size();
        }

        public Map<String, String> getAttributesMap() {
            return internalGetAttributes().getMap();
        }

        void mergeEmptyInputThroughGeneratedMessageSchema(CodedInputStream input) throws Exception {
            mergeFromAndMakeImmutableInternal(input, ExtensionRegistryLite.getEmptyRegistry());
        }

        private MapField<String, String> internalGetAttributes() {
            if (attributes_ == null) {
                return MapField.emptyMapField(AttributesDefaultEntryHolder.DEFAULT_ENTRY);
            }
            return attributes_;
        }

        @Override
        protected MapFieldReflectionAccessor internalGetMapFieldReflection(int number) {
            if (number == 1) {
                return internalGetAttributes();
            }
            throw new IllegalArgumentException("Invalid map field number: " + number);
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
            return new MapBackedMessage();
        }

        @Override
        public Descriptors.Descriptor getDescriptorForType() {
            return DESCRIPTOR;
        }

        @Override
        public MapBackedMessage getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        @Override
        public UnknownFieldSet getUnknownFields() {
            return UnknownFieldSet.getDefaultInstance();
        }

        public static final class AttributesDefaultEntryHolder {
            public static final MapEntry<String, String> DEFAULT_ENTRY = MapEntry.newDefaultInstance(
                    DESCRIPTOR.findNestedTypeByName("AttributesEntry"),
                    WireFormat.FieldType.STRING,
                    "",
                    WireFormat.FieldType.STRING,
                    "");

            private AttributesDefaultEntryHolder() {
            }
        }
    }

    private static final class TestProtoDescriptors {
        private static final Descriptors.FileDescriptor FILE_DESCRIPTOR = fileDescriptor();

        private static Descriptors.Descriptor messageDescriptor() {
            return FILE_DESCRIPTOR.findMessageTypeByName("MapBackedMessage");
        }

        private static Descriptors.FileDescriptor fileDescriptor() {
            DescriptorProtos.DescriptorProto attributesEntry = DescriptorProtos.DescriptorProto.newBuilder()
                    .setName("AttributesEntry")
                    .addField(field("key", 1))
                    .addField(field("value", 2))
                    .setOptions(DescriptorProtos.MessageOptions.newBuilder().setMapEntry(true))
                    .build();
            DescriptorProtos.DescriptorProto mapBackedMessage = DescriptorProtos.DescriptorProto.newBuilder()
                    .setName("MapBackedMessage")
                    .addNestedType(attributesEntry)
                    .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName("attributes")
                            .setNumber(1)
                            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName(".schemautil.MapBackedMessage.AttributesEntry")
                            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED))
                    .build();
            DescriptorProtos.FileDescriptorProto fileDescriptorProto = DescriptorProtos.FileDescriptorProto.newBuilder()
                    .setName("schema_util_probe.proto")
                    .setPackage("schemautil")
                    .setSyntax("proto3")
                    .addMessageType(mapBackedMessage)
                    .build();
            try {
                return Descriptors.FileDescriptor.buildFrom(fileDescriptorProto, new Descriptors.FileDescriptor[0]);
            } catch (Descriptors.DescriptorValidationException exception) {
                throw new IllegalStateException("Unable to build test descriptor", exception);
            }
        }

        private static DescriptorProtos.FieldDescriptorProto field(String name, int number) {
            return DescriptorProtos.FieldDescriptorProto.newBuilder()
                    .setName(name)
                    .setNumber(number)
                    .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                    .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .build();
        }
    }
}
