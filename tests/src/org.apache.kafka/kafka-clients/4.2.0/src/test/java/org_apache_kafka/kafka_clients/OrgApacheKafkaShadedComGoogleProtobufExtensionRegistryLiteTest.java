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
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.Descriptor;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.DescriptorValidationException;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.FieldDescriptor;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.FileDescriptor;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionLite;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistry;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistry.ExtensionInfo;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistryLite;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessage;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessage.GeneratedExtension;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufExtensionRegistryLiteTest {

    @Test
    void addExtensionLiteDelegatesToFullRegistryExtensionRegistration() {
        ExtensionRegistryLite registry = ExtensionRegistry.newInstance();
        GeneratedExtension<Message, Integer> extension = GeneratedMessage.newFileScopedGeneratedExtension(
                Integer.class,
                null);
        extension.internalInit(DescriptorOuter.fileScopedExtension);

        registry.add((ExtensionLite<Message, Integer>) extension);

        ExtensionInfo registeredExtension = ((ExtensionRegistry) registry)
                .findImmutableExtensionByNumber(DescriptorOuter.extendedMessageDescriptor, 100);
        assertThat(registeredExtension).isNotNull();
        assertThat(registeredExtension.descriptor).isSameAs(DescriptorOuter.fileScopedExtension);
        assertThat(registeredExtension.descriptor.getFullName())
                .isEqualTo("forge.kafka.clients.protobuf.extensionregistrylite.file_scoped_extension");
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class DescriptorOuter {
        private static final FileDescriptor descriptor;
        private static final Descriptor extendedMessageDescriptor;
        private static final FieldDescriptor fileScopedExtension;

        static {
            try {
                descriptor = FileDescriptor.buildFrom(fileDescriptorProto(), new FileDescriptor[0]);
                extendedMessageDescriptor = descriptor.findMessageTypeByName("ExtendedMessage");
                fileScopedExtension = descriptor.findExtensionByName("file_scoped_extension");
            } catch (DescriptorValidationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private DescriptorOuter() {
        }

        private static FileDescriptorProto fileDescriptorProto() {
            DescriptorProto message = DescriptorProto.newBuilder()
                    .setName("ExtendedMessage")
                    .addExtensionRange(DescriptorProto.ExtensionRange.newBuilder()
                            .setStart(100)
                            .setEnd(101))
                    .build();

            FieldDescriptorProto extension = FieldDescriptorProto.newBuilder()
                    .setName("file_scoped_extension")
                    .setNumber(100)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .setType(FieldDescriptorProto.Type.TYPE_INT32)
                    .setExtendee(".forge.kafka.clients.protobuf.extensionregistrylite.ExtendedMessage")
                    .build();

            return FileDescriptorProto.newBuilder()
                    .setName("extension_registry_lite_test.proto")
                    .setPackage("forge.kafka.clients.protobuf.extensionregistrylite")
                    .setSyntax("proto2")
                    .addMessageType(message)
                    .addExtension(extension)
                    .build();
        }
    }
}
