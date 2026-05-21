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
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufDescriptorsInnerFileDescriptorTest {
    private static final String PROTO_PACKAGE = "forge.kafka.clients.file_descriptor";
    private static final String DEPENDENCY_PROTO_NAME = "forge_file_descriptor_dependency.proto";

    @Test
    void testInternalBuildGeneratedFileFromResolvesDescriptorsThroughGeneratedClass() {
        FileDescriptor fileDescriptor = FileDescriptor.internalBuildGeneratedFileFrom(
                descriptorData(dependentDescriptorProto()),
                OrgApacheKafkaShadedComGoogleProtobufDescriptorsInnerFileDescriptorTest.class,
                new String[] {DependencyDescriptorHolder.class.getName()},
                new String[] {DEPENDENCY_PROTO_NAME});

        assertThat(fileDescriptor.getName()).isEqualTo("forge_file_descriptor_dependent.proto");
        assertThat(fileDescriptor.getDependencies()).containsExactly(DependencyDescriptorHolder.descriptor);
        FieldDescriptor dependencyField = fileDescriptor
                .findMessageTypeByName("DependentMessage")
                .findFieldByName("dependency");
        assertThat(dependencyField.getMessageType().getFullName())
                .isEqualTo(PROTO_PACKAGE + ".DependencyMessage");
    }

    private static String[] descriptorData(FileDescriptorProto descriptorProto) {
        return new String[] {new String(descriptorProto.toByteArray(), StandardCharsets.ISO_8859_1)};
    }

    private static FileDescriptorProto dependentDescriptorProto() {
        DescriptorProto dependentMessage = DescriptorProto.newBuilder()
                .setName("DependentMessage")
                .addField(FieldDescriptorProto.newBuilder()
                        .setName("dependency")
                        .setNumber(1)
                        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName("." + PROTO_PACKAGE + ".DependencyMessage"))
                .build();

        return FileDescriptorProto.newBuilder()
                .setName("forge_file_descriptor_dependent.proto")
                .setPackage(PROTO_PACKAGE)
                .setSyntax("proto3")
                .addDependency(DEPENDENCY_PROTO_NAME)
                .addMessageType(dependentMessage)
                .build();
    }

    private static FileDescriptorProto dependencyDescriptorProto() {
        DescriptorProto dependencyMessage = DescriptorProto.newBuilder()
                .setName("DependencyMessage")
                .addField(FieldDescriptorProto.newBuilder()
                        .setName("value")
                        .setNumber(1)
                        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(FieldDescriptorProto.Type.TYPE_STRING))
                .build();

        return FileDescriptorProto.newBuilder()
                .setName(DEPENDENCY_PROTO_NAME)
                .setPackage(PROTO_PACKAGE)
                .setSyntax("proto3")
                .addMessageType(dependencyMessage)
                .build();
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class DependencyDescriptorHolder {
        public static final FileDescriptor descriptor = buildDependencyDescriptor();

        private DependencyDescriptorHolder() {
        }

        private static FileDescriptor buildDependencyDescriptor() {
            try {
                return FileDescriptor.buildFrom(dependencyDescriptorProto(), new FileDescriptor[0]);
            } catch (DescriptorValidationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }
}
