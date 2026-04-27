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
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessage;
import org.junit.jupiter.api.Test;

public class GeneratedMessageAnonymous4Test {

    @Test
    void getDescriptorLoadsFileScopedExtensionDescriptorFromDescriptorHolderClass() {
        GeneratedMessage.GeneratedExtension<?, ?> extension = GeneratedMessage.newFileScopedGeneratedExtension(
            DescriptorProtos.FileOptions.class,
            DescriptorProtos.FileOptions.getDefaultInstance(),
            ExtensionDescriptorHolder.class.getName(),
            "test_file_options"
        );

        Descriptors.FieldDescriptor descriptor = extension.getDescriptor();

        assertThat(descriptor).isSameAs(ExtensionDescriptorHolder.descriptor.findExtensionByName("test_file_options"));
        assertThat(descriptor.getName()).isEqualTo("test_file_options");
        assertThat(descriptor.getContainingType().getFullName()).isEqualTo("google.protobuf.FileOptions");
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
                    .setName("generated_message_anonymous4_test.proto")
                    .setSyntax("proto2")
                    .addDependency("google/protobuf/descriptor.proto")
                    .addExtension(
                        DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName("test_file_options")
                            .setNumber(50001)
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
