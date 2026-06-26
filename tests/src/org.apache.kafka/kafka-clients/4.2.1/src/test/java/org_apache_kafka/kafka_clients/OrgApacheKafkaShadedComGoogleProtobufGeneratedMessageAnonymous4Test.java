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
import org.apache.kafka.shaded.com.google.protobuf.DynamicMessage;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessage;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessage.GeneratedExtension;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufGeneratedMessageAnonymous4Test {

    @Test
    void fileScopedGeneratedExtensionLoadsDescriptorFromOuterClass() {
        GeneratedExtension<DynamicMessage, Object> extension = GeneratedMessage.newFileScopedGeneratedExtension(
                DescriptorOuter.class,
                null,
                DescriptorOuter.class.getName(),
                "marker");

        FieldDescriptor descriptor = extension.getDescriptor();

        assertThat(descriptor.getName()).isEqualTo("marker");
        assertThat(descriptor.getNumber()).isEqualTo(100);
        assertThat(descriptor.getContainingType().getFullName())
                .isEqualTo("forge.kafka.clients.protobuf.extension.Host");
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
            DescriptorProto host = DescriptorProto.newBuilder()
                    .setName("Host")
                    .addExtensionRange(ExtensionRange.newBuilder()
                            .setStart(100)
                            .setEnd(536870912))
                    .build();

            FieldDescriptorProto marker = FieldDescriptorProto.newBuilder()
                    .setName("marker")
                    .setNumber(100)
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                    .setType(FieldDescriptorProto.Type.TYPE_INT32)
                    .setExtendee(".forge.kafka.clients.protobuf.extension.Host")
                    .build();

            return FileDescriptorProto.newBuilder()
                    .setName("generated_message_file_extension.proto")
                    .setPackage("forge.kafka.clients.protobuf.extension")
                    .addMessageType(host)
                    .addExtension(marker)
                    .build();
        }
    }
}
