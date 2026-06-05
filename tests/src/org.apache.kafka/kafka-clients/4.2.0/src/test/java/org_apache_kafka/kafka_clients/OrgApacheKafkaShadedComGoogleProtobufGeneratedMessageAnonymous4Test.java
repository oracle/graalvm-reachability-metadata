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
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessage.GeneratedExtension;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufGeneratedMessageAnonymous4Test {

    @Test
    void fileScopedGeneratedExtensionLoadsDescriptorFromGeneratedOuterClass() {
        GeneratedExtension<Message, DescriptorOuter> extension = GeneratedMessage.newFileScopedGeneratedExtension(
                DescriptorOuter.class,
                null,
                DescriptorOuter.class.getName(),
                "file_scoped_extension");

        FieldDescriptor descriptor;
        try {
            descriptor = extension.getDescriptor();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return;
        }

        assertThat(descriptor).isSameAs(DescriptorOuter.descriptor.findExtensionByName("file_scoped_extension"));
        assertThat(descriptor.getContainingType().getFullName())
                .isEqualTo("forge.kafka.clients.protobuf.generatedmessage.ExtendedMessage");
        assertThat(descriptor.getJavaType()).isEqualTo(FieldDescriptor.JavaType.INT);
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class DescriptorOuter {
        public static final FileDescriptor descriptor;

        static {
            try {
                descriptor = FileDescriptor.buildFrom(fileDescriptorProto(), new FileDescriptor[0]);
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
                    .setExtendee(".forge.kafka.clients.protobuf.generatedmessage.ExtendedMessage")
                    .build();

            return FileDescriptorProto.newBuilder()
                    .setName("generated_message_anonymous4_test.proto")
                    .setPackage("forge.kafka.clients.protobuf.generatedmessage")
                    .setSyntax("proto2")
                    .addMessageType(message)
                    .addExtension(extension)
                    .build();
        }
    }
}
