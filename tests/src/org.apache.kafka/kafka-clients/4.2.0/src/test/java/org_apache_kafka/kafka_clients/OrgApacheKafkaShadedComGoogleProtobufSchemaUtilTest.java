/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.kafka.shaded.com.google.protobuf.CodedInputStream;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.Descriptor;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistryLite;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageV3;
import org.apache.kafka.shaded.com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.shaded.com.google.protobuf.MapEntry;
import org.apache.kafka.shaded.com.google.protobuf.MapField;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.apache.kafka.shaded.com.google.protobuf.NullValue;
import org.apache.kafka.shaded.com.google.protobuf.Struct;
import org.apache.kafka.shaded.com.google.protobuf.Value;
import org.apache.kafka.shaded.com.google.protobuf.WireFormat.FieldType;
import org.junit.jupiter.api.Test;

public class OrgApacheKafkaShadedComGoogleProtobufSchemaUtilTest {
    @Test
    void generatedV3SchemaReadsMapDefaultEntryHolder() throws InvalidProtocolBufferException {
        Value value = Value.newBuilder()
                .setNullValue(NullValue.NULL_VALUE)
                .build();
        byte[] serialized = Struct.newBuilder()
                .putFields("nullable", value)
                .build()
                .toByteArray();

        MapBackedGeneratedMessage parsed = MapBackedGeneratedMessage.parseFrom(serialized);

        assertThat(parsed.getFieldsMap())
                .containsEntry("nullable", value);
    }

    @SuppressWarnings({"checkstyle:MemberName", "serial"})
    public static final class MapBackedGeneratedMessage extends GeneratedMessageV3 {
        private static final MapBackedGeneratedMessage DEFAULT_INSTANCE =
                new MapBackedGeneratedMessage();

        private MapField<String, Value> fields_;

        public static MapBackedGeneratedMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        public static MapBackedGeneratedMessage parseFrom(byte[] data)
                throws InvalidProtocolBufferException {
            MapBackedGeneratedMessage message = new MapBackedGeneratedMessage();
            CodedInputStream input = CodedInputStream.newInstance(data);
            message.mergeFromAndMakeImmutableInternal(
                    input, ExtensionRegistryLite.getEmptyRegistry());
            input.checkLastTagWas(0);
            return message;
        }

        public Map<String, Value> getFieldsMap() {
            return internalGetFields().getMap();
        }

        @Override
        public Descriptor getDescriptorForType() {
            return Struct.getDescriptor();
        }

        @Override
        public MapBackedGeneratedMessage getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        @Override
        public Message.Builder newBuilderForType() {
            throw new UnsupportedOperationException("Builder is not needed for this test message");
        }

        @Override
        public Message.Builder toBuilder() {
            throw new UnsupportedOperationException("Builder is not needed for this test message");
        }

        @Override
        protected FieldAccessorTable internalGetFieldAccessorTable() {
            throw new UnsupportedOperationException(
                    "Field accessors are not needed for this test message");
        }

        @Override
        protected Message.Builder newBuilderForType(BuilderParent parent) {
            throw new UnsupportedOperationException("Builder is not needed for this test message");
        }

        private MapField<String, Value> internalGetFields() {
            if (fields_ == null) {
                return MapField.emptyMapField(FieldsDefaultEntryHolder.defaultEntry);
            }
            return fields_;
        }

        public static final class FieldsDefaultEntryHolder {
            public static final MapEntry<String, Value> defaultEntry = MapEntry.newDefaultInstance(
                    Struct.getDescriptor().findFieldByNumber(1).getMessageType(),
                    FieldType.STRING,
                    "",
                    FieldType.MESSAGE,
                    Value.getDefaultInstance());

            private FieldsDefaultEntryHolder() {
            }
        }
    }
}
