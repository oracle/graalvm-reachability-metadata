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
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessage;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufGeneratedMessageAnonymous4Test {
    private static final String PROTO_PACKAGE = "forge.kafka.clients.generated_message_extension";
    private static final String EXTENSION_NAME = "extended_value";

    @Test
    void testFileScopedGeneratedExtensionLoadsDescriptorFromOuterClass() {
        GeneratedMessage.GeneratedExtension<Message, ?> extension = GeneratedMessage.newFileScopedGeneratedExtension(
                        OrgApacheKafkaShadedComGoogleProtobufGeneratedMessageAnonymous4Test.class,
                        null,
                        ExtensionDescriptorHolder.class.getName(),
                        EXTENSION_NAME);

        FieldDescriptor descriptor = extension.getDescriptor();

        assertThat(descriptor.getName()).isEqualTo(EXTENSION_NAME);
        assertThat(descriptor.getFullName()).isEqualTo(PROTO_PACKAGE + "." + EXTENSION_NAME);
        assertThat(descriptor.getContainingType().getFullName()).isEqualTo(PROTO_PACKAGE + ".ExtendedMessage");
        assertThat(descriptor.getFile()).isSameAs(ExtensionDescriptorHolder.descriptor);
    }

    private static FileDescriptorProto extensionDescriptorProto() {
        DescriptorProto extendedMessage = DescriptorProto.newBuilder()
                .setName("ExtendedMessage")
                .addExtensionRange(DescriptorProto.ExtensionRange.newBuilder()
                        .setStart(100)
                        .setEnd(101))
                .build();

        FieldDescriptorProto extensionField = FieldDescriptorProto.newBuilder()
                .setName(EXTENSION_NAME)
                .setNumber(100)
                .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .setType(FieldDescriptorProto.Type.TYPE_STRING)
                .setExtendee("." + PROTO_PACKAGE + ".ExtendedMessage")
                .build();

        return FileDescriptorProto.newBuilder()
                .setName("forge_generated_message_extension.proto")
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
