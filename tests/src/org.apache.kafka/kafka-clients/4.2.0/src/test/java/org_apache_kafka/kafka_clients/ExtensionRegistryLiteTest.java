/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors;
import org.apache.kafka.shaded.com.google.protobuf.Extension;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionLite;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistry;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistryLite;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.apache.kafka.shaded.com.google.protobuf.WireFormat;
import org.junit.jupiter.api.Test;

public class ExtensionRegistryLiteTest {
    private static final int EXTENSION_FIELD_NUMBER = 100;
    private static final String EXTENSION_FIELD_NAME = "coverage_number";
    private static final String EXTENSION_FULL_NAME = "coverage.coverage_number";
    private static final Descriptors.FileDescriptor FILE_DESCRIPTOR = buildFileDescriptor();
    private static final Descriptors.Descriptor CONTAINER_DESCRIPTOR =
            FILE_DESCRIPTOR.findMessageTypeByName("Container");
    private static final Descriptors.FieldDescriptor EXTENSION_DESCRIPTOR =
            FILE_DESCRIPTOR.findExtensionByName(EXTENSION_FIELD_NAME);
    private static final TestExtension EXTENSION = new TestExtension(EXTENSION_DESCRIPTOR);

    @Test
    void addsFullRuntimeExtensionThroughLiteRegistryApi() {
        ExtensionRegistryLite registry = ExtensionRegistry.newInstance();
        ExtensionLite<Message, Integer> liteExtension = EXTENSION;

        registry.add(liteExtension);

        ExtensionRegistry fullRegistry = (ExtensionRegistry) registry;
        ExtensionRegistry.ExtensionInfo infoByName = fullRegistry.findImmutableExtensionByName(EXTENSION_FULL_NAME);
        ExtensionRegistry.ExtensionInfo infoByNumber =
                fullRegistry.findImmutableExtensionByNumber(CONTAINER_DESCRIPTOR, EXTENSION_FIELD_NUMBER);
        assertNotNull(infoByName);
        assertSame(infoByName, infoByNumber);
        assertSame(EXTENSION_DESCRIPTOR, infoByName.descriptor);
    }

    private static Descriptors.FileDescriptor buildFileDescriptor() {
        try {
            return Descriptors.FileDescriptor.buildFrom(fileDescriptorProto(), new Descriptors.FileDescriptor[0]);
        } catch (Descriptors.DescriptorValidationException exception) {
            throw new IllegalStateException("Unable to build extension descriptor", exception);
        }
    }

    private static DescriptorProtos.FileDescriptorProto fileDescriptorProto() {
        DescriptorProtos.DescriptorProto container = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Container")
                .addExtensionRange(DescriptorProtos.DescriptorProto.ExtensionRange.newBuilder()
                        .setStart(EXTENSION_FIELD_NUMBER)
                        .setEnd(EXTENSION_FIELD_NUMBER + 1))
                .build();
        DescriptorProtos.FieldDescriptorProto extensionField = DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName(EXTENSION_FIELD_NAME)
                .setNumber(EXTENSION_FIELD_NUMBER)
                .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
                .setExtendee(".coverage.Container")
                .build();
        return DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("coverage_extension.proto")
                .setPackage("coverage")
                .setSyntax("proto2")
                .addMessageType(container)
                .addExtension(extensionField)
                .build();
    }

    private static final class TestExtension extends Extension<Message, Integer> {
        private final Descriptors.FieldDescriptor descriptor;

        private TestExtension(Descriptors.FieldDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public int getNumber() {
            return descriptor.getNumber();
        }

        @Override
        public WireFormat.FieldType getLiteType() {
            return WireFormat.FieldType.INT32;
        }

        @Override
        public boolean isRepeated() {
            return false;
        }

        @Override
        public Integer getDefaultValue() {
            return 0;
        }

        @Override
        public Message getMessageDefaultInstance() {
            return null;
        }

        @Override
        public Descriptors.FieldDescriptor getDescriptor() {
            return descriptor;
        }

        @Override
        protected ExtensionType getExtensionType() {
            return ExtensionType.IMMUTABLE;
        }

        @Override
        protected Object fromReflectionType(Object value) {
            return value;
        }

        @Override
        protected Object singularFromReflectionType(Object value) {
            return value;
        }

        @Override
        protected Object toReflectionType(Object value) {
            return value;
        }

        @Override
        protected Object singularToReflectionType(Object value) {
            return value;
        }
    }
}
