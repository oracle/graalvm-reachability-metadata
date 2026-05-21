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
import org.apache.kafka.shaded.com.google.protobuf.WireFormat.FieldType;

import java.util.Map;

public final class Android32ProtobufMapParsing {

    private Android32ProtobufMapParsing() {
    }

    public static String parseMapEntryValue() throws InvalidProtocolBufferException {
        MapBackedMessage message = MapBackedMessage.parseFrom(new byte[] {
            10, 10, 8, 11, 18, 6, 'e', 'l', 'e', 'v', 'e', 'n'
        });
        return message.getEntriesMap().get(11);
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class MapBackedMessage extends GeneratedMessageV3 {
        private static final Descriptor DESCRIPTOR;
        private static final FieldAccessorTable FIELD_ACCESSOR_TABLE;
        private static final MapBackedMessage DEFAULT_INSTANCE;

        static {
            try {
                FileDescriptor fileDescriptor = FileDescriptor.buildFrom(fileDescriptorProto(), new FileDescriptor[0]);
                DESCRIPTOR = fileDescriptor.findMessageTypeByName("Android32AccessorMapBackedMessage");
                FIELD_ACCESSOR_TABLE = new FieldAccessorTable(DESCRIPTOR, new String[] {"Entries"});
                DEFAULT_INSTANCE = new MapBackedMessage();
            } catch (DescriptorValidationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private MapField<Integer, String> entries_;

        private MapBackedMessage() {
            entries_ = MapField.newMapField(EntriesDefaultEntryHolder.DEFAULT_ENTRY);
        }

        public static MapBackedMessage parseFrom(byte[] data) throws InvalidProtocolBufferException {
            MapBackedMessage message = new MapBackedMessage();
            CodedInputStream input = CodedInputStream.newInstance(data);
            message.mergeFromAndMakeImmutableInternal(input, ExtensionRegistryLite.getEmptyRegistry());
            return message;
        }

        public Map<Integer, String> getEntriesMap() {
            return entries_.getMap();
        }

        public static MapBackedMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
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
            DescriptorProto entriesEntry = DescriptorProto.newBuilder()
                    .setName("EntriesEntry")
                    .setOptions(MessageOptions.newBuilder().setMapEntry(true))
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("key")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(FieldDescriptorProto.Type.TYPE_INT32))
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("value")
                            .setNumber(2)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(FieldDescriptorProto.Type.TYPE_STRING))
                    .build();

            DescriptorProto message = DescriptorProto.newBuilder()
                    .setName("Android32AccessorMapBackedMessage")
                    .addNestedType(entriesEntry)
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("entries")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                            .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName(".forge.kafka.clients.protobuf."
                                    + "Android32AccessorMapBackedMessage.EntriesEntry"))
                    .build();

            return FileDescriptorProto.newBuilder()
                    .setName("unsafe_util_android32_memory_accessor_map_default_entry_test.proto")
                    .setPackage("forge.kafka.clients.protobuf")
                    .setSyntax("proto3")
                    .addMessageType(message)
                    .build();
        }

        public static final class EntriesDefaultEntryHolder {
            public static final MapEntry<Integer, String> DEFAULT_ENTRY = MapEntry.newDefaultInstance(
                    DESCRIPTOR.findNestedTypeByName("EntriesEntry"),
                    FieldType.INT32,
                    0,
                    FieldType.STRING,
                    "");

            private EntriesDefaultEntryHolder() {
            }
        }
    }
}
