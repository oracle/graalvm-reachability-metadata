/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.MessageOptions;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.MapEntry;
import com.google.protobuf.MapField;
import com.google.protobuf.Message;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.WireFormat.FieldType;
import org.junit.jupiter.api.Test;

public class SchemaUtilTest {
    @Test
    void serializesAndParsesLiteMapMessage() throws Exception {
        Value value = Value.newBuilder().setStringValue("protobuf-javalite").build();
        Struct message = Struct.newBuilder().putFields("library", value).build();

        Struct parsed = Struct.parseFrom(message.toByteArray());

        assertThat(parsed.getFieldsOrThrow("library").getStringValue())
                .isEqualTo("protobuf-javalite");
    }

    @Test
    void createsGeneratedMessageV3SchemaForMapMessage() throws Exception {
        GeneratedFullMessage message = new GeneratedFullMessage();

        message.mergeEmptyPayloadThroughGeneratedMessageV3Schema();

        assertThat(message.getDescriptorForType().getFullName())
                .isEqualTo("forge.protobuf.GeneratedFullMessage");
        assertThat(message.getDefaultInstanceForType())
                .isSameAs(GeneratedFullMessage.getDefaultInstance());
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class GeneratedFullMessage extends GeneratedMessageV3 {
        private static final Descriptor DESCRIPTOR;
        private static final FieldAccessorTable FIELD_ACCESSOR_TABLE;
        private static final GeneratedFullMessage DEFAULT_INSTANCE;

        static {
            try {
                FileDescriptor fileDescriptor = FileDescriptor.buildFrom(
                        fileDescriptorProto(), new FileDescriptor[0]);
                DESCRIPTOR = fileDescriptor.findMessageTypeByName("GeneratedFullMessage");
                FIELD_ACCESSOR_TABLE = new FieldAccessorTable(
                        DESCRIPTOR, new String[] {"Metadata"});
                DEFAULT_INSTANCE = new GeneratedFullMessage();
            } catch (DescriptorValidationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private MapField<String, String> metadata_ =
                MapField.emptyMapField(MetadataDefaultEntryHolder.defaultEntry);

        private GeneratedFullMessage() {
        }

        public static GeneratedFullMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        void mergeEmptyPayloadThroughGeneratedMessageV3Schema() throws Exception {
            CodedInputStream input = CodedInputStream.newInstance(new byte[0]);
            mergeFromAndMakeImmutableInternal(input, ExtensionRegistryLite.getEmptyRegistry());
        }

        @Override
        protected FieldAccessorTable internalGetFieldAccessorTable() {
            return FIELD_ACCESSOR_TABLE;
        }

        @Override
        protected Message.Builder newBuilderForType(BuilderParent parent) {
            throw new UnsupportedOperationException("Builder is not needed by this test fixture.");
        }

        @Override
        public Message.Builder newBuilderForType() {
            throw new UnsupportedOperationException("Builder is not needed by this test fixture.");
        }

        @Override
        public Message.Builder toBuilder() {
            throw new UnsupportedOperationException("Builder is not needed by this test fixture.");
        }

        @Override
        public GeneratedFullMessage getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        private static FileDescriptorProto fileDescriptorProto() {
            DescriptorProto metadataEntry = DescriptorProto.newBuilder()
                    .setName("MetadataEntry")
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("key")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(FieldDescriptorProto.Type.TYPE_STRING))
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("value")
                            .setNumber(2)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(FieldDescriptorProto.Type.TYPE_STRING))
                    .setOptions(MessageOptions.newBuilder().setMapEntry(true).build())
                    .build();
            DescriptorProto message = DescriptorProto.newBuilder()
                    .setName("GeneratedFullMessage")
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("metadata")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                            .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName(".forge.protobuf.GeneratedFullMessage.MetadataEntry"))
                    .addNestedType(metadataEntry)
                    .build();
            return FileDescriptorProto.newBuilder()
                    .setName("schema_util_test.proto")
                    .setPackage("forge.protobuf")
                    .setSyntax("proto3")
                    .addMessageType(message)
                    .build();
        }

        public static final class MetadataDefaultEntryHolder {
            public static final MapEntry<String, String> defaultEntry = MapEntry
                    .<String, String>newDefaultInstance(
                            DESCRIPTOR.findNestedTypeByName("MetadataEntry"),
                            FieldType.STRING,
                            "",
                            FieldType.STRING,
                            "");
        }
    }
}
