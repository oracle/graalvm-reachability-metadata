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
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.DescriptorValidationException;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.FieldDescriptor;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors.FileDescriptor;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufDescriptorsInnerFileDescriptorTest {

    @Test
    void testGeneratedFileBuildLoadsDependencyDescriptorByClassName() {
        String[] descriptorData = descriptorData(mainFileDescriptorProto());
        String[] dependencyClassNames = {DependencyDescriptor.class.getName()};
        String[] dependencyFileNames = {DependencyDescriptor.descriptor.getName()};

        FileDescriptor fileDescriptor = FileDescriptor.internalBuildGeneratedFileFrom(
                descriptorData,
                OrgApacheKafkaShadedComGoogleProtobufDescriptorsInnerFileDescriptorTest.class,
                dependencyClassNames,
                dependencyFileNames);

        Descriptor dependentMessage = fileDescriptor.findMessageTypeByName("MessageUsingDependency");
        FieldDescriptor dependencyField = dependentMessage.findFieldByName("dependency_value");

        assertThat(fileDescriptor.getDependencies()).containsExactly(DependencyDescriptor.descriptor);
        assertThat(dependencyField.getMessageType()).isSameAs(DependencyDescriptor.MESSAGE_DESCRIPTOR);
    }

    private static String[] descriptorData(FileDescriptorProto fileDescriptorProto) {
        byte[] descriptorBytes = fileDescriptorProto.toByteArray();
        return new String[] {new String(descriptorBytes, StandardCharsets.ISO_8859_1)};
    }

    private static FileDescriptorProto mainFileDescriptorProto() {
        DescriptorProto message = DescriptorProto.newBuilder()
                .setName("MessageUsingDependency")
                .addField(FieldDescriptorProto.newBuilder()
                        .setName("dependency_value")
                        .setNumber(1)
                        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName(".forge.kafka.clients.protobuf.dependency.DependencyMessage"))
                .build();

        return FileDescriptorProto.newBuilder()
                .setName("message_using_dependency.proto")
                .setPackage("forge.kafka.clients.protobuf")
                .setSyntax("proto3")
                .addDependency(DependencyDescriptor.descriptor.getName())
                .addMessageType(message)
                .build();
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class DependencyDescriptor {
        public static final FileDescriptor descriptor;
        public static final Descriptor MESSAGE_DESCRIPTOR;

        static {
            try {
                descriptor = FileDescriptor.buildFrom(fileDescriptorProto(), new FileDescriptor[0]);
                MESSAGE_DESCRIPTOR = descriptor.findMessageTypeByName("DependencyMessage");
            } catch (DescriptorValidationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private DependencyDescriptor() {
        }

        private static FileDescriptorProto fileDescriptorProto() {
            DescriptorProto message = DescriptorProto.newBuilder()
                    .setName("DependencyMessage")
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("name")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(FieldDescriptorProto.Type.TYPE_STRING))
                    .build();

            return FileDescriptorProto.newBuilder()
                    .setName("dependency_descriptor_test.proto")
                    .setPackage("forge.kafka.clients.protobuf.dependency")
                    .setSyntax("proto3")
                    .addMessageType(message)
                    .build();
        }
    }
}
