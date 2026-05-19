/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13;

import java.util.Map;

import org.apache.pekko.protobufv3.internal.CodedInputStream;
import org.apache.pekko.protobufv3.internal.DescriptorProtos;
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto.Label;
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto.Type;
import org.apache.pekko.protobufv3.internal.Descriptors;
import org.apache.pekko.protobufv3.internal.ExtensionRegistryLite;
import org.apache.pekko.protobufv3.internal.GeneratedMessageV3;
import org.apache.pekko.protobufv3.internal.InvalidProtocolBufferException;
import org.apache.pekko.protobufv3.internal.MapEntry;
import org.apache.pekko.protobufv3.internal.MapField;
import org.apache.pekko.protobufv3.internal.Message;
import org.apache.pekko.protobufv3.internal.WireFormat;

public final class SchemaUtilMapMessage extends GeneratedMessageV3 {
    private static final String NO_BUILDERS_MESSAGE =
            "Builders are not needed for this coverage fixture";
    private static final Descriptors.FileDescriptor FILE_DESCRIPTOR = buildFileDescriptor();
    private static final Descriptors.Descriptor MESSAGE_DESCRIPTOR =
            FILE_DESCRIPTOR.findMessageTypeByName("SchemaUtilMapMessage");
    private static final GeneratedMessageV3.FieldAccessorTable FIELD_ACCESSOR_TABLE =
            new GeneratedMessageV3.FieldAccessorTable(MESSAGE_DESCRIPTOR, new String[] {"Labels"});
    private static final SchemaUtilMapMessage DEFAULT_INSTANCE = new SchemaUtilMapMessage();

    private MapField<String, Integer> labels_ =
            MapField.emptyMapField(LabelsDefaultEntryHolder.defaultEntry);

    public static SchemaUtilMapMessage getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    public int getLabelsCount() {
        return labels_.getMap().size();
    }

    public Map<String, Integer> getLabelsMap() {
        return labels_.getMap();
    }

    public void parseEmptyInputThroughSchema() throws InvalidProtocolBufferException {
        mergeFromAndMakeImmutableInternal(
                CodedInputStream.newInstance(new byte[0]),
                ExtensionRegistryLite.getEmptyRegistry());
    }

    @Override
    protected GeneratedMessageV3.FieldAccessorTable internalGetFieldAccessorTable() {
        return FIELD_ACCESSOR_TABLE;
    }

    @Override
    protected Message.Builder newBuilderForType(GeneratedMessageV3.BuilderParent parent) {
        throw new UnsupportedOperationException(NO_BUILDERS_MESSAGE);
    }

    @Override
    public Message.Builder newBuilderForType() {
        throw new UnsupportedOperationException(NO_BUILDERS_MESSAGE);
    }

    @Override
    public Message.Builder toBuilder() {
        throw new UnsupportedOperationException(NO_BUILDERS_MESSAGE);
    }

    @Override
    public Message getDefaultInstanceForType() {
        return DEFAULT_INSTANCE;
    }

    public static final class LabelsDefaultEntryHolder {
        public static final MapEntry<String, Integer> defaultEntry = MapEntry.newDefaultInstance(
                MESSAGE_DESCRIPTOR.findNestedTypeByName("LabelsEntry"),
                WireFormat.FieldType.STRING,
                "",
                WireFormat.FieldType.INT32,
                0);

        private LabelsDefaultEntryHolder() {
        }
    }

    private static Descriptors.FileDescriptor buildFileDescriptor() {
        DescriptorProtos.DescriptorProto labelsEntry = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("LabelsEntry")
                .setOptions(DescriptorProtos.MessageOptions.newBuilder().setMapEntry(true))
                .addField(field("key", 1, Label.LABEL_OPTIONAL, Type.TYPE_STRING))
                .addField(field("value", 2, Label.LABEL_OPTIONAL, Type.TYPE_INT32))
                .build();

        DescriptorProtos.DescriptorProto message = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("SchemaUtilMapMessage")
                .addNestedType(labelsEntry)
                .addField(field("labels", 1, Label.LABEL_REPEATED, Type.TYPE_MESSAGE)
                        .setTypeName(".schema_util.SchemaUtilMapMessage.LabelsEntry"))
                .build();

        DescriptorProtos.FileDescriptorProto file =
                DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("schema_util_map_message.proto")
                .setPackage("schema_util")
                .setSyntax("proto3")
                .addMessageType(message)
                .build();

        try {
            return Descriptors.FileDescriptor.buildFrom(file, new Descriptors.FileDescriptor[0]);
        } catch (Descriptors.DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static DescriptorProtos.FieldDescriptorProto.Builder field(
            String name,
            int number,
            Label label,
            Type type) {
        return DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName(name)
                .setNumber(number)
                .setLabel(label)
                .setType(type);
    }
}
