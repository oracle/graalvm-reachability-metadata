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

    @Test
    void addGeneratedExtensionThroughLiteApiRegistersItInFullRegistry() throws Exception {
        FileDescriptor fileDescriptor = FileDescriptor.buildFrom(fileDescriptorProto(), new FileDescriptor[0]);
        Descriptor extendeeDescriptor = fileDescriptor.findMessageTypeByName("Extendee");
        FieldDescriptor extensionDescriptor = fileDescriptor.findExtensionByName("extra_value");
        GeneratedMessage.GeneratedExtension<Message, Integer> extension =
                GeneratedMessage.newFileScopedGeneratedExtension(Integer.class, null);
        extension.internalInit(extensionDescriptor);

        ExtensionRegistryLite registryLite = ExtensionRegistryLite.newInstance();
        registryLite.add(extension);

        assertThat(registryLite).isInstanceOf(ExtensionRegistry.class);
        ExtensionRegistry registry = (ExtensionRegistry) registryLite;
        ExtensionInfo extensionInfo = registry.findImmutableExtensionByNumber(extendeeDescriptor, 100);
        assertThat(extensionInfo).isNotNull();
        assertThat(extensionInfo.descriptor).isSameAs(extensionDescriptor);
    }

    private static FileDescriptorProto fileDescriptorProto() {
        DescriptorProto extendeeMessage = DescriptorProto.newBuilder()
                .setName("Extendee")
                .addExtensionRange(DescriptorProto.ExtensionRange.newBuilder()
                        .setStart(100)
                        .setEnd(101))
                .build();

        FieldDescriptorProto extensionField = FieldDescriptorProto.newBuilder()
                .setName("extra_value")
                .setNumber(100)
                .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .setType(FieldDescriptorProto.Type.TYPE_INT32)
                .setExtendee(".forge.kafka.clients.protobuf.Extendee")
                .build();

        return FileDescriptorProto.newBuilder()
                .setName("extension_registry_lite_test.proto")
                .setPackage("forge.kafka.clients.protobuf")
                .addMessageType(extendeeMessage)
                .addExtension(extensionField)
                .build();
    }
}
