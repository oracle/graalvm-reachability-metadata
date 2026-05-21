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
import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos.MessageOptions;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.Descriptor;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.DescriptorValidationException;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.FileDescriptor;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistryLite;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageV3;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageV3.FieldAccessorTable;
import org.apache.kafka.shaded.com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.shaded.com.google.protobuf.MapEntry;
import org.apache.kafka.shaded.com.google.protobuf.MapField;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.apache.kafka.shaded.com.google.protobuf.WireFormat;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufSchemaUtilTest {

    @Test
    void generatedMessageV3SchemaLoadsMapDefaultEntryHolder() throws Exception {
        SchemaUtilMapMessage message = new SchemaUtilMapMessage();

        message.mergeEmptyPayloadThroughGeneratedMessageV3Schema();

        assertThat(message.getDescriptorForType().findFieldByName("values").isMapField()).isTrue();
        assertThat(message.getValuesMap()).isEmpty();
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class SchemaUtilMapMessage extends GeneratedMessageV3 {
        private static final Descriptor DESCRIPTOR;
        private static final FieldAccessorTable FIELD_ACCESSOR_TABLE;
        private static final SchemaUtilMapMessage DEFAULT_INSTANCE;

        static {
            try {
                FileDescriptor fileDescriptor = FileDescriptor.buildFrom(fileDescriptorProto(), new FileDescriptor[0]);
                DESCRIPTOR = fileDescriptor.findMessageTypeByName("SchemaUtilMapMessage");
                FIELD_ACCESSOR_TABLE = new FieldAccessorTable(DESCRIPTOR, new String[] {"Values"});
                DEFAULT_INSTANCE = new SchemaUtilMapMessage();
            } catch (DescriptorValidationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private MapField<String, Integer> values_;

        public SchemaUtilMapMessage() {
            values_ = MapField.emptyMapField(ValuesDefaultEntryHolder.DEFAULT_ENTRY);
        }

        public static SchemaUtilMapMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        public Map<String, Integer> getValuesMap() {
            return Collections.unmodifiableMap(values_.getMap());
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
            DescriptorProto mapEntry = DescriptorProto.newBuilder()
                    .setName("ValuesEntry")
                    .setOptions(MessageOptions.newBuilder().setMapEntry(true))
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("key")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(FieldDescriptorProto.Type.TYPE_STRING))
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("value")
                            .setNumber(2)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(FieldDescriptorProto.Type.TYPE_INT32))
                    .build();

            DescriptorProto message = DescriptorProto.newBuilder()
                    .setName("SchemaUtilMapMessage")
                    .addNestedType(mapEntry)
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("values")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                            .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName(".forge.kafka.clients.protobuf.SchemaUtilMapMessage.ValuesEntry"))
                    .build();

            return FileDescriptorProto.newBuilder()
                    .setName("schema_util_map_message_test.proto")
                    .setPackage("forge.kafka.clients.protobuf")
                    .setSyntax("proto3")
                    .addMessageType(message)
                    .build();
        }

        public static final class ValuesDefaultEntryHolder {
            public static final MapEntry<String, Integer> DEFAULT_ENTRY = MapEntry.newDefaultInstance(
                    DESCRIPTOR.findNestedTypeByName("ValuesEntry"),
                    WireFormat.FieldType.STRING,
                    "",
                    WireFormat.FieldType.INT32,
                    0);

            private ValuesDefaultEntryHolder() {
            }
        }
    }
}
