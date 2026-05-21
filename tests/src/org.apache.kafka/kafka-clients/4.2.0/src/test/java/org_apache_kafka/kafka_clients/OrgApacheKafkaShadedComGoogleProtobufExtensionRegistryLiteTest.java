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
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.DescriptorValidationException;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.FieldDescriptor;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.FileDescriptor;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionLite;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistry;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistry.ExtensionInfo;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistryLite;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessage;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufExtensionRegistryLiteTest {
    private static final String PROTO_PACKAGE = "forge.kafka.clients.extension_registry_lite";
    private static final String EXTENSION_NAME = "registered_value";
    private static final int EXTENSION_NUMBER = 100;

    @Test
    void testAddingFullRuntimeExtensionThroughLiteApiRegistersItInFullRegistry() {
        ExtensionRegistryLite registry = ExtensionRegistry.newInstance();
        ExtensionLite<Message, ?> extension = GeneratedMessage.newFileScopedGeneratedExtension(
                OrgApacheKafkaShadedComGoogleProtobufExtensionRegistryLiteTest.class,
                null,
                ExtensionDescriptorHolder.class.getName(),
                EXTENSION_NAME);

        registry.add(extension);

        FieldDescriptor extensionDescriptor = ExtensionDescriptorHolder.descriptor.findExtensionByName(EXTENSION_NAME);
        ExtensionRegistry fullRegistry = (ExtensionRegistry) registry;
        ExtensionInfo extensionInfo = fullRegistry.findMutableExtensionByName(extensionDescriptor.getFullName());

        assertThat(extensionInfo).isNotNull();
        assertThat(extensionInfo.descriptor).isSameAs(extensionDescriptor);
        assertThat(fullRegistry.findMutableExtensionByNumber(
                extensionDescriptor.getContainingType(),
                EXTENSION_NUMBER).descriptor).isSameAs(extensionDescriptor);
    }

    private static FileDescriptorProto extensionDescriptorProto() {
        DescriptorProto extendedMessage = DescriptorProto.newBuilder()
                .setName("ExtendedMessage")
                .addExtensionRange(DescriptorProto.ExtensionRange.newBuilder()
                        .setStart(EXTENSION_NUMBER)
                        .setEnd(EXTENSION_NUMBER + 1))
                .build();

        FieldDescriptorProto extensionField = FieldDescriptorProto.newBuilder()
                .setName(EXTENSION_NAME)
                .setNumber(EXTENSION_NUMBER)
                .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .setType(FieldDescriptorProto.Type.TYPE_STRING)
                .setExtendee("." + PROTO_PACKAGE + ".ExtendedMessage")
                .build();

        return FileDescriptorProto.newBuilder()
                .setName("forge_extension_registry_lite.proto")
                .setPackage(PROTO_PACKAGE)
                .setSyntax("proto2")
                .addMessageType(extendedMessage)
                .addExtension(extensionField)
                .build();
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class ExtensionDescriptorHolder {
        public static final FileDescriptor descriptor = buildDescriptor();

        private ExtensionDescriptorHolder() {
        }

        private static FileDescriptor buildDescriptor() {
            try {
                return FileDescriptor.buildFrom(extensionDescriptorProto(), new FileDescriptor[0]);
            } catch (DescriptorValidationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }
}
