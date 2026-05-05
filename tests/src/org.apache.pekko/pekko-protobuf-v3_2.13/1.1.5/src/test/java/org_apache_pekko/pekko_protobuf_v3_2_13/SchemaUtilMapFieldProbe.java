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
import org.apache.pekko.protobufv3.internal.Descriptors;
import org.apache.pekko.protobufv3.internal.ExtensionRegistryLite;
import org.apache.pekko.protobufv3.internal.GeneratedMessageV3;
import org.apache.pekko.protobufv3.internal.InvalidProtocolBufferException;
import org.apache.pekko.protobufv3.internal.MapEntry;
import org.apache.pekko.protobufv3.internal.MapField;
import org.apache.pekko.protobufv3.internal.Message;
import org.apache.pekko.protobufv3.internal.WireFormat;

public final class SchemaUtilMapFieldProbe extends GeneratedMessageV3 {
    private static final Descriptors.Descriptor DESCRIPTOR = buildDescriptor();
    private static final SchemaUtilMapFieldProbe DEFAULT_INSTANCE = new SchemaUtilMapFieldProbe();

    private MapField<String, String> entries_ = MapField.emptyMapField(EntriesDefaultEntryHolder.defaultEntry);

    public static SchemaUtilMapFieldProbe getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    public void initializeEmptyMapSchema() throws InvalidProtocolBufferException {
        CodedInputStream input = CodedInputStream.newInstance(new byte[0]);
        mergeFromAndMakeImmutableInternal(input, ExtensionRegistryLite.getEmptyRegistry());
    }

    public Map<String, String> getEntriesMap() {
        return entries_.getMap();
    }

    @Override
    public Descriptors.Descriptor getDescriptorForType() {
        return DESCRIPTOR;
    }

    @Override
    public SchemaUtilMapFieldProbe getDefaultInstanceForType() {
        return DEFAULT_INSTANCE;
    }

    @Override
    public Message.Builder newBuilderForType() {
        throw new UnsupportedOperationException("Builder is not needed for map schema coverage");
    }

    @Override
    public Message.Builder toBuilder() {
        throw new UnsupportedOperationException("Builder is not needed for map schema coverage");
    }

    @Override
    protected FieldAccessorTable internalGetFieldAccessorTable() {
        throw new UnsupportedOperationException("FieldAccessorTable is not needed for map schema coverage");
    }

    @Override
    protected Message.Builder newBuilderForType(BuilderParent parent) {
        throw new UnsupportedOperationException("Builder is not needed for map schema coverage");
    }

    private static Descriptors.Descriptor buildDescriptor() {
        DescriptorProtos.DescriptorProto entriesEntry = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("EntriesEntry")
                .setOptions(DescriptorProtos.MessageOptions.newBuilder().setMapEntry(true))
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("key")
                        .setNumber(1)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("value")
                        .setNumber(2)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .build();

        DescriptorProtos.DescriptorProto probe = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("MapProbe")
                .addNestedType(entriesEntry)
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("entries")
                        .setNumber(1)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName(".schema_util_coverage.MapProbe.EntriesEntry"))
                .build();

        DescriptorProtos.FileDescriptorProto file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("schema_util_map_field_probe.proto")
                .setPackage("schema_util_coverage")
                .setSyntax("proto3")
                .addMessageType(probe)
                .build();
        try {
            Descriptors.FileDescriptor descriptor = Descriptors.FileDescriptor.buildFrom(
                    file,
                    new Descriptors.FileDescriptor[0]);
            return descriptor.findMessageTypeByName("MapProbe");
        } catch (Descriptors.DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static final class EntriesDefaultEntryHolder {
        public static final MapEntry<String, String> defaultEntry = MapEntry.newDefaultInstance(
                DESCRIPTOR.findNestedTypeByName("EntriesEntry"),
                WireFormat.FieldType.STRING,
                "",
                WireFormat.FieldType.STRING,
                "");
    }
}
