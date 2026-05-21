/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos.DescriptorProto;
import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos.DescriptorProto.ExtensionRange;
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

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void testFileScopedGeneratedExtensionLoadsDescriptorFromOuterClassName() {
        GeneratedMessage.GeneratedExtension<Message, Integer> extension =
                GeneratedMessage.newFileScopedGeneratedExtension(
                        DescriptorAnchor.class,
                        null,
                        ExtensionDescriptor.class.getName(),
                        "test_extension");

        FieldDescriptor descriptor = extension.getDescriptor();

        assertThat(descriptor.getName()).isEqualTo("test_extension");
        assertThat(descriptor.getNumber()).isEqualTo(100);
        assertThat(descriptor.getContainingType().getFullName())
                .isEqualTo("forge.kafka.clients.protobuf.GeneratedMessageAnonymous4Target");
    }

    private static final class DescriptorAnchor {
        private DescriptorAnchor() {
        }
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class ExtensionDescriptor {
        public static final FileDescriptor descriptor;

        static {
            try {
                descriptor = FileDescriptor.buildFrom(fileDescriptorProto(), new FileDescriptor[0]);
            } catch (DescriptorValidationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private ExtensionDescriptor() {
        }

        private static FileDescriptorProto fileDescriptorProto() {
            DescriptorProto targetMessage = DescriptorProto.newBuilder()
                    .setName("GeneratedMessageAnonymous4Target")
                    .addExtensionRange(ExtensionRange.newBuilder()
                            .setStart(100)
                            .setEnd(101))
                    .build();

            FieldDescriptorProto extension = FieldDescriptorProto.newBuilder()
                    .setName("test_extension")
                    .setNumber(100)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .setType(FieldDescriptorProto.Type.TYPE_INT32)
                    .setExtendee(".forge.kafka.clients.protobuf.GeneratedMessageAnonymous4Target")
                    .build();

            return FileDescriptorProto.newBuilder()
                    .setName("generated_message_anonymous4_extension.proto")
                    .setPackage("forge.kafka.clients.protobuf")
                    .addMessageType(targetMessage)
                    .addExtension(extension)
                    .build();
        }
    }
}
