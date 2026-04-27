/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistry;
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistryLite;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessage;
import org.junit.jupiter.api.Test;

public class ExtensionRegistryLiteTest {

    private static final String EXTENSION_NAME = "extension_registry_lite_test_file_option";
    private static final int EXTENSION_NUMBER = 50011;

    @Test
    void addDelegatesGeneratedMessageExtensionsToTheFullRegistry() {
        ExtensionRegistryLite registry = ExtensionRegistryLite.newInstance();
        GeneratedMessage.GeneratedExtension<?, ?> extension = GeneratedMessage.newFileScopedGeneratedExtension(
            DescriptorProtos.FileOptions.class,
            DescriptorProtos.FileOptions.getDefaultInstance(),
            ExtensionDescriptorHolder.class.getName(),
            EXTENSION_NAME
        );

        registry.add(extension);

        assertThat(registry).isInstanceOf(ExtensionRegistry.class);

        ExtensionRegistry fullRegistry = (ExtensionRegistry) registry;
        Descriptors.FieldDescriptor expectedDescriptor =
            ExtensionDescriptorHolder.descriptor.findExtensionByName(EXTENSION_NAME);
        ExtensionRegistry.ExtensionInfo extensionByName = fullRegistry.findMutableExtensionByName(
            expectedDescriptor.getFullName()
        );
        ExtensionRegistry.ExtensionInfo extensionByNumber =
            fullRegistry.findMutableExtensionByNumber(expectedDescriptor.getContainingType(), EXTENSION_NUMBER);

        assertThat(extensionByName).isNotNull();
        assertThat(extensionByName.descriptor).isSameAs(expectedDescriptor);
        assertThat(extensionByNumber).isNotNull();
        assertThat(extensionByNumber.descriptor).isSameAs(expectedDescriptor);
    }

    public static final class ExtensionDescriptorHolder {
        public static final Descriptors.FileDescriptor descriptor = createExtensionDescriptor();

        private ExtensionDescriptorHolder() {
        }
    }

    private static Descriptors.FileDescriptor createExtensionDescriptor() {
        try {
            return Descriptors.FileDescriptor.buildFrom(
                DescriptorProtos.FileDescriptorProto.newBuilder()
                    .setName("extension_registry_lite_test.proto")
                    .setSyntax("proto2")
                    .addDependency("google/protobuf/descriptor.proto")
                    .addExtension(
                        DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName(EXTENSION_NAME)
                            .setNumber(EXTENSION_NUMBER)
                            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL)
                            .setExtendee(".google.protobuf.FileOptions")
                            .build()
                    )
                    .build(),
                new Descriptors.FileDescriptor[] {DescriptorProtos.getDescriptor()}
            );
        } catch (Descriptors.DescriptorValidationException e) {
            throw new IllegalStateException(e);
        }
    }
}
