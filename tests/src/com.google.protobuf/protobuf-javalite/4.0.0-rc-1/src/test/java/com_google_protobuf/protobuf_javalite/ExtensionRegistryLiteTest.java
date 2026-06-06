/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.ExtensionRegistry.ExtensionInfo;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.Message;
import org.junit.jupiter.api.Test;

public class ExtensionRegistryLiteTest {
    @Test
    void forwardsFullRuntimeExtensionToFullRegistry() throws Exception {
        FileDescriptor fileDescriptor = FileDescriptor.buildFrom(
                fileDescriptorProto(), new FileDescriptor[0]);
        Descriptor hostDescriptor = fileDescriptor.findMessageTypeByName("Host");
        FieldDescriptor extensionDescriptor = fileDescriptor
                .findExtensionByName("sample_extension");
        GeneratedMessage.GeneratedExtension<Message, Integer> extension =
                GeneratedMessage.newFileScopedGeneratedExtension(Integer.class, null);
        extension.internalInit(extensionDescriptor);

        ExtensionRegistryLite registry = ExtensionRegistryLite.newInstance();

        assertThat(registry).isInstanceOf(ExtensionRegistry.class);
        registry.add(extension);
        ExtensionInfo extensionInfo = ((ExtensionRegistry) registry)
                .findImmutableExtensionByNumber(hostDescriptor, 100);
        assertThat(extensionInfo).isNotNull();
        assertThat(extensionInfo.descriptor.getFullName())
                .isEqualTo("forge.protobuf.registry.sample_extension");
    }

    private static FileDescriptorProto fileDescriptorProto() {
        DescriptorProto hostMessage = DescriptorProto.newBuilder()
                .setName("Host")
                .addExtensionRange(DescriptorProto.ExtensionRange.newBuilder()
                        .setStart(100)
                        .setEnd(101))
                .build();
        FieldDescriptorProto extension = FieldDescriptorProto.newBuilder()
                .setName("sample_extension")
                .setNumber(100)
                .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .setType(FieldDescriptorProto.Type.TYPE_INT32)
                .setExtendee(".forge.protobuf.registry.Host")
                .build();
        return FileDescriptorProto.newBuilder()
                .setName("extension_registry_lite_test.proto")
                .setPackage("forge.protobuf.registry")
                .setSyntax("proto2")
                .addMessageType(hostMessage)
                .addExtension(extension)
                .build();
    }
}
