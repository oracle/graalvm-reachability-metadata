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
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.FieldDescriptor;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.FileDescriptor;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistry;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistry.ExtensionInfo;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistryLite;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessage;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufExtensionRegistryLiteTest {
    private static final int EXTENSION_NUMBER = 100;

    @Test
    void addExtensionLiteDelegatesToFullRegistryThroughPublicExtensionApi() throws Exception {
        FileDescriptor fileDescriptor = FileDescriptor.buildFrom(fileDescriptorProto(), new FileDescriptor[0]);
        Descriptor targetDescriptor = fileDescriptor.findMessageTypeByName("TargetMessage");
        FieldDescriptor extensionDescriptor = fileDescriptor.findExtensionByName("test_extension");
        GeneratedMessage.GeneratedExtension<Message, Integer> extension =
                GeneratedMessage.newFileScopedGeneratedExtension(Integer.class, null);
        extension.internalInit(extensionDescriptor);

        ExtensionRegistryLite registry = ExtensionRegistryLite.newInstance();
        registry.add(extension);

        assertThat(registry).isInstanceOf(ExtensionRegistry.class);
        ExtensionInfo registeredExtension = ((ExtensionRegistry) registry)
                .findImmutableExtensionByNumber(targetDescriptor, EXTENSION_NUMBER);
        assertThat(registeredExtension).isNotNull();
        assertThat(registeredExtension.descriptor).isSameAs(extensionDescriptor);
    }

    private static FileDescriptorProto fileDescriptorProto() {
        DescriptorProto targetMessage = DescriptorProto.newBuilder()
                .setName("TargetMessage")
                .addExtensionRange(DescriptorProto.ExtensionRange.newBuilder()
                        .setStart(EXTENSION_NUMBER)
                        .setEnd(EXTENSION_NUMBER + 1))
                .build();

        FieldDescriptorProto extension = FieldDescriptorProto.newBuilder()
                .setName("test_extension")
                .setExtendee(".forge.kafka.clients.protobuf.registry.TargetMessage")
                .setNumber(EXTENSION_NUMBER)
                .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .setType(FieldDescriptorProto.Type.TYPE_INT32)
                .build();

        return FileDescriptorProto.newBuilder()
                .setName("extension_registry_lite_test.proto")
                .setPackage("forge.kafka.clients.protobuf.registry")
                .setSyntax("proto2")
                .addMessageType(targetMessage)
                .addExtension(extension)
                .build();
    }
}
