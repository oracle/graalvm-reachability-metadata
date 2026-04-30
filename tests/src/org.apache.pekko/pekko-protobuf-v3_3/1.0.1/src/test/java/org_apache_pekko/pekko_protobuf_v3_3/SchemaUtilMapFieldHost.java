/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3;

import java.util.Objects;

import org.apache.pekko.protobufv3.internal.CodedInputStream;
import org.apache.pekko.protobufv3.internal.DescriptorProtos.DescriptorProto;
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto;
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto.Label;
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto.Type;
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FileDescriptorProto;
import org.apache.pekko.protobufv3.internal.DescriptorProtos.MessageOptions;
import org.apache.pekko.protobufv3.internal.Descriptors.Descriptor;
import org.apache.pekko.protobufv3.internal.Descriptors.DescriptorValidationException;
import org.apache.pekko.protobufv3.internal.Descriptors.FileDescriptor;
import org.apache.pekko.protobufv3.internal.ExtensionRegistryLite;
import org.apache.pekko.protobufv3.internal.GeneratedMessageV3;
import org.apache.pekko.protobufv3.internal.InvalidProtocolBufferException;
import org.apache.pekko.protobufv3.internal.MapEntry;
import org.apache.pekko.protobufv3.internal.MapField;
import org.apache.pekko.protobufv3.internal.Message;
import org.apache.pekko.protobufv3.internal.Parser;
import org.apache.pekko.protobufv3.internal.UnknownFieldSet;
import org.apache.pekko.protobufv3.internal.WireFormat;

public final class SchemaUtilMapFieldHost extends GeneratedMessageV3 {
    private static final FileDescriptor FILE = createFileDescriptor();
    private static final Descriptor HOST_DESCRIPTOR = FILE.findMessageTypeByName("SchemaUtilMapFieldHost");
    private static final GeneratedMessageV3.FieldAccessorTable FIELD_ACCESSOR_TABLE =
            new GeneratedMessageV3.FieldAccessorTable(HOST_DESCRIPTOR, new String[] {"Fields"});
    private static final SchemaUtilMapFieldHost DEFAULT_INSTANCE = new SchemaUtilMapFieldHost();

    private MapField<String, String> fields_ = MapField.emptyMapField(FieldsDefaultEntryHolder.defaultEntry);

    private SchemaUtilMapFieldHost() {
    }

    public static SchemaUtilMapFieldHost getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    public static SchemaUtilMapFieldHost newMutable() {
        return new SchemaUtilMapFieldHost();
    }

    public static Object initializedMapDefaultEntryForTests() {
        return FieldsDefaultEntryHolder.defaultEntry;
    }

    public void parseEmptyInputWithGeneratedMessageSchema() throws InvalidProtocolBufferException {
        CodedInputStream input = CodedInputStream.newInstance(new byte[0]);
        mergeFromAndMakeImmutableInternal(input, ExtensionRegistryLite.getEmptyRegistry());
    }

    @Override
    public Descriptor getDescriptorForType() {
        return HOST_DESCRIPTOR;
    }

    @Override
    protected GeneratedMessageV3.FieldAccessorTable internalGetFieldAccessorTable() {
        return FIELD_ACCESSOR_TABLE;
    }

    @Override
    public Parser<? extends GeneratedMessageV3> getParserForType() {
        return null;
    }

    @Override
    public Message.Builder newBuilderForType() {
        return null;
    }

    @Override
    protected Message.Builder newBuilderForType(BuilderParent parent) {
        return null;
    }

    @Override
    public Message.Builder toBuilder() {
        return null;
    }

    @Override
    public UnknownFieldSet getUnknownFields() {
        return UnknownFieldSet.getDefaultInstance();
    }

    @Override
    public Message getDefaultInstanceForType() {
        return DEFAULT_INSTANCE;
    }

    @Override
    public boolean equals(Object other) {
        return this == other;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getDescriptorForType().getFullName());
    }

    private static FileDescriptor createFileDescriptor() {
        FieldDescriptorProto keyField = FieldDescriptorProto.newBuilder()
                .setName("key")
                .setNumber(1)
                .setLabel(Label.LABEL_OPTIONAL)
                .setType(Type.TYPE_STRING)
                .build();
        FieldDescriptorProto valueField = FieldDescriptorProto.newBuilder()
                .setName("value")
                .setNumber(2)
                .setLabel(Label.LABEL_OPTIONAL)
                .setType(Type.TYPE_STRING)
                .build();
        DescriptorProto fieldsEntry = DescriptorProto.newBuilder()
                .setName("FieldsEntry")
                .addField(keyField)
                .addField(valueField)
                .setOptions(MessageOptions.newBuilder().setMapEntry(true))
                .build();
        FieldDescriptorProto fieldsField = FieldDescriptorProto.newBuilder()
                .setName("fields")
                .setNumber(1)
                .setLabel(Label.LABEL_REPEATED)
                .setType(Type.TYPE_MESSAGE)
                .setTypeName(".dynamicaccess.SchemaUtilMapFieldHost.FieldsEntry")
                .build();
        DescriptorProto hostType = DescriptorProto.newBuilder()
                .setName("SchemaUtilMapFieldHost")
                .addField(fieldsField)
                .addNestedType(fieldsEntry)
                .build();
        FileDescriptorProto fileProto = FileDescriptorProto.newBuilder()
                .setName("schema_util_map_field_host.proto")
                .setPackage("dynamicaccess")
                .setSyntax("proto3")
                .addMessageType(hostType)
                .build();

        try {
            return FileDescriptor.buildFrom(fileProto, new FileDescriptor[0]);
        } catch (DescriptorValidationException e) {
            throw new IllegalStateException("Unable to build schema-util map-field descriptor", e);
        }
    }

    static final class FieldsDefaultEntryHolder {
        static MapEntry<String, String> defaultEntry = MapEntry.newDefaultInstance(
                HOST_DESCRIPTOR.findNestedTypeByName("FieldsEntry"),
                WireFormat.FieldType.STRING,
                "",
                WireFormat.FieldType.STRING,
                "");

        private FieldsDefaultEntryHolder() {
        }
    }
}
