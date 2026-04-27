/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.apache.kafka.shaded.com.google.protobuf.CodedInputStream;
import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistryLite;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessageV3;
import org.apache.kafka.shaded.com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.shaded.com.google.protobuf.MapEntry;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.apache.kafka.shaded.com.google.protobuf.WireFormat;
import org.junit.jupiter.api.Test;

public class SchemaUtilTest {

    @Test
    void schemaInitializationLoadsGeneratedStyleMapDefaultEntryHolder() {
        assertThat(MapMessageDescriptors.MESSAGE_DESCRIPTOR.findFieldByName("labels").isMapField()).isTrue();
        assertThatNoException().isThrownBy(() -> new SchemaUtilMapMessage().initializeSchemaFromEmptyInput());
    }

    private static final class MapMessageDescriptors {
        private static final Descriptors.Descriptor MESSAGE_DESCRIPTOR;
        private static final Descriptors.Descriptor LABELS_ENTRY_DESCRIPTOR;

        static {
            try {
                DescriptorProtos.DescriptorProto labelsEntry = DescriptorProtos.DescriptorProto.newBuilder()
                    .setName("LabelsEntry")
                    .setOptions(
                        DescriptorProtos.MessageOptions.newBuilder()
                            .setMapEntry(true)
                            .build()
                    )
                    .addField(
                        DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName("key")
                            .setNumber(1)
                            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                            .build()
                    )
                    .addField(
                        DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName("value")
                            .setNumber(2)
                            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
                            .build()
                    )
                    .build();

                DescriptorProtos.DescriptorProto message = DescriptorProtos.DescriptorProto.newBuilder()
                    .setName("SchemaUtilMapMessage")
                    .addNestedType(labelsEntry)
                    .addField(
                        DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName("labels")
                            .setNumber(1)
                            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
                            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName(".schema.util.test.SchemaUtilMapMessage.LabelsEntry")
                            .build()
                    )
                    .build();

                Descriptors.FileDescriptor fileDescriptor = Descriptors.FileDescriptor.buildFrom(
                    DescriptorProtos.FileDescriptorProto.newBuilder()
                        .setName("schema_util_test.proto")
                        .setPackage("schema.util.test")
                        .setSyntax("proto3")
                        .addMessageType(message)
                        .build(),
                    new Descriptors.FileDescriptor[0]
                );

                MESSAGE_DESCRIPTOR = fileDescriptor.findMessageTypeByName("SchemaUtilMapMessage");
                LABELS_ENTRY_DESCRIPTOR = MESSAGE_DESCRIPTOR.findNestedTypeByName("LabelsEntry");
            } catch (Descriptors.DescriptorValidationException e) {
                throw new IllegalStateException(e);
            }
        }

        private MapMessageDescriptors() {
        }
    }

    public static final class SchemaUtilMapMessage extends GeneratedMessageV3 {
        private static final SchemaUtilMapMessage DEFAULT_INSTANCE = new SchemaUtilMapMessage();

        @SuppressWarnings("unused")
        private Object labels_;

        public static SchemaUtilMapMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        public void initializeSchemaFromEmptyInput() throws InvalidProtocolBufferException {
            mergeFromAndMakeImmutableInternal(
                CodedInputStream.newInstance(new byte[0]),
                ExtensionRegistryLite.getEmptyRegistry()
            );
        }

        @Override
        public SchemaUtilMapMessage getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        @Override
        public Descriptors.Descriptor getDescriptorForType() {
            return MapMessageDescriptors.MESSAGE_DESCRIPTOR;
        }

        @Override
        protected GeneratedMessageV3.FieldAccessorTable internalGetFieldAccessorTable() {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        protected Message.Builder newBuilderForType(GeneratedMessageV3.BuilderParent parent) {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        public Message.Builder newBuilderForType() {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        public Message.Builder toBuilder() {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        static final class LabelsDefaultEntryHolder {
            static final MapEntry<String, Integer> defaultEntry = MapEntry.newDefaultInstance(
                MapMessageDescriptors.LABELS_ENTRY_DESCRIPTOR,
                WireFormat.FieldType.STRING,
                "",
                WireFormat.FieldType.INT32,
                0
            );

            private LabelsDefaultEntryHolder() {
            }
        }
    }
}
