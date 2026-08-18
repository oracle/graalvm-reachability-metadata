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
    void buildsGeneratedFileDescriptorWithDependencyClassNames() {
        FileDescriptor descriptor = FileDescriptor.internalBuildGeneratedFileFrom(
                descriptorDataParts(mainDescriptorProto()),
                OrgApacheKafkaShadedComGoogleProtobufDescriptorsInnerFileDescriptorTest.class,
                new String[] {DependencyProto.class.getName()},
                new String[] {DependencyProto.descriptor.getName()});

        Descriptor container = descriptor.findMessageTypeByName("Container");
        FieldDescriptor dependencyField = container.findFieldByName("dependency");

        assertThat(descriptor.getName()).isEqualTo("file_descriptor_reflection_test.proto");
        assertThat(descriptor.getDependencies()).containsExactly(DependencyProto.descriptor);
        assertThat(dependencyField.getMessageType().getFullName())
                .isEqualTo("forge.kafka.clients.protobuf.dependency.Dependency");
    }

    private static String[] descriptorDataParts(FileDescriptorProto proto) {
        String descriptorData = new String(proto.toByteArray(), StandardCharsets.ISO_8859_1);
        return new String[] {descriptorData};
    }

    private static FileDescriptorProto mainDescriptorProto() {
        DescriptorProto container = DescriptorProto.newBuilder()
                .setName("Container")
                .addField(FieldDescriptorProto.newBuilder()
                        .setName("dependency")
                        .setNumber(1)
                        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName(".forge.kafka.clients.protobuf.dependency.Dependency"))
                .build();

        return FileDescriptorProto.newBuilder()
                .setName("file_descriptor_reflection_test.proto")
                .setPackage("forge.kafka.clients.protobuf.main")
                .setSyntax("proto3")
                .addDependency(DependencyProto.descriptor.getName())
                .addMessageType(container)
                .build();
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class DependencyProto {
        public static final FileDescriptor descriptor;

        static {
            try {
                descriptor = FileDescriptor.buildFrom(dependencyDescriptorProto(), new FileDescriptor[0]);
            } catch (DescriptorValidationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private DependencyProto() {
        }

        private static FileDescriptorProto dependencyDescriptorProto() {
            DescriptorProto dependency = DescriptorProto.newBuilder()
                    .setName("Dependency")
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("name")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(FieldDescriptorProto.Type.TYPE_STRING))
                    .build();

            return FileDescriptorProto.newBuilder()
                    .setName("dependency_descriptor.proto")
                    .setPackage("forge.kafka.clients.protobuf.dependency")
                    .setSyntax("proto3")
                    .addMessageType(dependency)
                    .build();
        }
    }
}
