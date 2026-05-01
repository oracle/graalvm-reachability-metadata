/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_protobuf_3_7;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.thirdparty.protobuf.DescriptorProtos;
import org.apache.hadoop.thirdparty.protobuf.Descriptors;
import org.apache.hadoop.thirdparty.protobuf.DynamicMessage;
import org.apache.hadoop.thirdparty.protobuf.GeneratedMessage;
import org.apache.hadoop.thirdparty.protobuf.Message;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class GeneratedMessageAnonymous4Test {
    private static final String EXTENSION_FILE_NAME = "coverage/generated_message_extension.proto";
    private static final String PACKAGE_NAME = "coverage.generatedmessage";
    private static final String TARGET_TYPE_NAME = "Target";
    private static final String EXTENSION_VALUE_TYPE_NAME = "ExtensionValue";
    private static final String EXTENSION_NAME = "tracked_extension";
    private static final int EXTENSION_NUMBER = 100;

    @Test
    void fileScopedGeneratedExtensionLoadsDescriptorFromOuterClassName() {
        GeneratedMessage.GeneratedExtension<Message, DynamicMessage> extension =
                GeneratedMessage.newFileScopedGeneratedExtension(
                        DynamicMessage.class,
                        ExtensionDescriptorHolder.extensionValueDefaultInstance,
                        ExtensionDescriptorHolder.class.getName(),
                        EXTENSION_NAME
                );

        Descriptors.FieldDescriptor descriptor;
        try {
            descriptor = extension.getDescriptor();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return;
        }

        assertThat(descriptor.getName()).isEqualTo(EXTENSION_NAME);
        assertThat(descriptor.getNumber()).isEqualTo(EXTENSION_NUMBER);
        assertThat(descriptor.getContainingType().getFullName()).isEqualTo(PACKAGE_NAME + "." + TARGET_TYPE_NAME);
        assertThat(descriptor.getMessageType().getFullName())
                .isEqualTo(PACKAGE_NAME + "." + EXTENSION_VALUE_TYPE_NAME);
        assertThat(descriptor.getFile()).isSameAs(ExtensionDescriptorHolder.descriptor);
    }

    private static Descriptors.FileDescriptor buildExtensionFileDescriptor() {
        DescriptorProtos.FileDescriptorProto proto = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName(EXTENSION_FILE_NAME)
                .setPackage(PACKAGE_NAME)
                .addMessageType(DescriptorProtos.DescriptorProto.newBuilder()
                        .setName(TARGET_TYPE_NAME)
                        .addExtensionRange(DescriptorProtos.DescriptorProto.ExtensionRange.newBuilder()
                                .setStart(EXTENSION_NUMBER)
                                .setEnd(536870912)))
                .addMessageType(DescriptorProtos.DescriptorProto.newBuilder()
                        .setName(EXTENSION_VALUE_TYPE_NAME))
                .addExtension(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName(EXTENSION_NAME)
                        .setNumber(EXTENSION_NUMBER)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName("." + PACKAGE_NAME + "." + EXTENSION_VALUE_TYPE_NAME)
                        .setExtendee("." + PACKAGE_NAME + "." + TARGET_TYPE_NAME))
                .build();
        try {
            return Descriptors.FileDescriptor.buildFrom(proto, new Descriptors.FileDescriptor[0]);
        } catch (Descriptors.DescriptorValidationException exception) {
            throw new IllegalStateException("Test descriptor should be valid", exception);
        }
    }

    public static final class ExtensionDescriptorHolder {
        public static final Descriptors.FileDescriptor descriptor = buildExtensionFileDescriptor();
        public static final DynamicMessage extensionValueDefaultInstance = DynamicMessage.getDefaultInstance(
                descriptor.findMessageTypeByName(EXTENSION_VALUE_TYPE_NAME)
        );

        private ExtensionDescriptorHolder() {
        }
    }
}
