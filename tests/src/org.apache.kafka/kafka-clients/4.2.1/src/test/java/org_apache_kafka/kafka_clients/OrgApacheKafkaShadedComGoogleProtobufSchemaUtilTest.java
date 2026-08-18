/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos.DescriptorProto;
import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos.MessageOptions;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.Descriptor;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.DescriptorValidationException;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.FileDescriptor;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageV3;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageV3.FieldAccessorTable;
import org.apache.kafka.shaded.com.google.protobuf.MapEntry;
import org.apache.kafka.shaded.com.google.protobuf.MapField;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.apache.kafka.shaded.com.google.protobuf.WireFormat;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("checkstyle:MemberName")
public class OrgApacheKafkaShadedComGoogleProtobufSchemaUtilTest extends GeneratedMessageV3 {
    private static final Descriptor DESCRIPTOR;
    private static final FieldAccessorTable FIELD_ACCESSOR_TABLE;
    private static final OrgApacheKafkaShadedComGoogleProtobufSchemaUtilTest DEFAULT_INSTANCE;

    static {
        try {
            FileDescriptor fileDescriptor = FileDescriptor.buildFrom(fileDescriptorProto(), new FileDescriptor[0]);
            DESCRIPTOR = fileDescriptor.findMessageTypeByName("OrgApacheKafkaShadedComGoogleProtobufSchemaUtilTest");
            FIELD_ACCESSOR_TABLE = new FieldAccessorTable(DESCRIPTOR, new String[] {"Attributes"});
            DEFAULT_INSTANCE = new OrgApacheKafkaShadedComGoogleProtobufSchemaUtilTest();
        } catch (DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private MapField<String, Integer> attributes_;

    public OrgApacheKafkaShadedComGoogleProtobufSchemaUtilTest() {
        attributes_ = MapField.newMapField(AttributesDefaultEntryHolder.defaultEntry);
        attributes_.getMutableMap().put("client", 42);
    }

    @Test
    void generatedMessageV3MapFieldSchemaLoadsDefaultEntryHolder() {
        OrgApacheKafkaShadedComGoogleProtobufSchemaUtilTest message =
                new OrgApacheKafkaShadedComGoogleProtobufSchemaUtilTest();

        assertThat(message.getAttributesMap()).containsEntry("client", 42);
        assertThat(message.getAttributesCount()).isEqualTo(1);
        assertThat(message.getAttributesOrDefault("missing", 7)).isEqualTo(7);
        assertThat(message.getDescriptorForType().findFieldByName("attributes").isMapField()).isTrue();
    }

    public static OrgApacheKafkaShadedComGoogleProtobufSchemaUtilTest getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    public Map<String, Integer> getAttributesMap() {
        return internalGetAttributes().getMap();
    }

    public int getAttributesCount() {
        return internalGetAttributes().getMap().size();
    }

    public boolean containsAttributes(String key) {
        return internalGetAttributes().getMap().containsKey(key);
    }

    public int getAttributesOrDefault(String key, int defaultValue) {
        Map<String, Integer> map = internalGetAttributes().getMap();
        return map.containsKey(key) ? map.get(key) : defaultValue;
    }

    public int getAttributesOrThrow(String key) {
        Map<String, Integer> map = internalGetAttributes().getMap();
        if (!map.containsKey(key)) {
            throw new IllegalArgumentException();
        }
        return map.get(key);
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

    @SuppressWarnings("unused")
    private MapField<String, Integer> internalGetAttributes() {
        if (attributes_ == null) {
            return MapField.emptyMapField(AttributesDefaultEntryHolder.defaultEntry);
        }
        return attributes_;
    }

    private static FileDescriptorProto fileDescriptorProto() {
        DescriptorProto attributesEntry = DescriptorProto.newBuilder()
                .setName("AttributesEntry")
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
                .setName("OrgApacheKafkaShadedComGoogleProtobufSchemaUtilTest")
                .addNestedType(attributesEntry)
                .addField(FieldDescriptorProto.newBuilder()
                        .setName("attributes")
                        .setNumber(1)
                        .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                        .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName(
                                ".forge.kafka.clients.protobuf."
                                        + "OrgApacheKafkaShadedComGoogleProtobufSchemaUtilTest.AttributesEntry"))
                .build();

        return FileDescriptorProto.newBuilder()
                .setName("schema_util_map_default_entry_test.proto")
                .setPackage("forge.kafka.clients.protobuf")
                .setSyntax("proto3")
                .addMessageType(message)
                .build();
    }

    private static final class AttributesDefaultEntryHolder {
        static final MapEntry<String, Integer> defaultEntry = MapEntry.newDefaultInstance(
                DESCRIPTOR.findNestedTypeByName("AttributesEntry"),
                WireFormat.FieldType.STRING,
                "",
                WireFormat.FieldType.INT32,
                0);
    }
}
