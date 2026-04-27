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

public class GeneratedMessageTest {

    private static final String EXTENSION_NAME = "generated_message_test_optimize_mode";
    private static final int EXTENSION_NUMBER = 50021;

    @Test
    void enumFileScopedExtensionUsesGeneratedMessageEnumReflectionHelpersWhenSettingAndReadingValues() {
        GeneratedMessage.GeneratedExtension<DescriptorProtos.FileOptions, DescriptorProtos.FileOptions.OptimizeMode> extension =
            GeneratedMessage.newFileScopedGeneratedExtension(
                DescriptorProtos.FileOptions.OptimizeMode.class,
                null,
                EnumExtensionDescriptorHolder.class.getName(),
                EXTENSION_NAME
            );

        DescriptorProtos.FileOptions options = DescriptorProtos.FileOptions.newBuilder()
            .setExtension(extension, DescriptorProtos.FileOptions.OptimizeMode.LITE_RUNTIME)
            .build();

        assertThat(extension.getDescriptor())
            .isSameAs(EnumExtensionDescriptorHolder.descriptor.findExtensionByName(EXTENSION_NAME));
        assertThat(extension.getDescriptor().getJavaType()).isEqualTo(Descriptors.FieldDescriptor.JavaType.ENUM);
        assertThat(options.hasExtension(extension)).isTrue();
        assertThat(options.getExtension(extension)).isEqualTo(DescriptorProtos.FileOptions.OptimizeMode.LITE_RUNTIME);
    }

    public static final class EnumExtensionDescriptorHolder {
        public static final Descriptors.FileDescriptor descriptor = createExtensionDescriptor();

        private EnumExtensionDescriptorHolder() {
        }
    }

    private static Descriptors.FileDescriptor createExtensionDescriptor() {
        try {
            return Descriptors.FileDescriptor.buildFrom(
                DescriptorProtos.FileDescriptorProto.newBuilder()
                    .setName("generated_message_test.proto")
                    .setSyntax("proto2")
                    .addDependency("google/protobuf/descriptor.proto")
                    .addExtension(
                        DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName(EXTENSION_NAME)
                            .setNumber(EXTENSION_NUMBER)
                            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM)
                            .setTypeName(".google.protobuf.FileOptions.OptimizeMode")
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
