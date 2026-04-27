/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors;
import org.junit.jupiter.api.Test;

public class DescriptorsInnerFileDescriptorTest {

    private static final String DEPENDENCY_FILE_NAME = "descriptors_file_descriptor_test_dependency.proto";
    private static final String DEPENDENCY_PACKAGE_NAME = "kafka.test.dependency";
    private static final String DEPENDENCY_MESSAGE_NAME = "DependencyMessage";
    private static final Descriptors.FileDescriptor DEPENDENCY_DESCRIPTOR = createDependencyDescriptor();

    @Test
    void internalBuildGeneratedFileFromLoadsGeneratedDependencyDescriptorsFromNamedHolderClass() {
        Descriptors.FileDescriptor fileDescriptor = Descriptors.FileDescriptor.internalBuildGeneratedFileFrom(
            latin1DescriptorData(createDependentFileDescriptorProto()),
            DescriptorsInnerFileDescriptorTest.class,
            new String[] {DependencyDescriptorHolder.class.getName()},
            new String[] {DEPENDENCY_FILE_NAME}
        );

        Descriptors.Descriptor usesDependency = fileDescriptor.findMessageTypeByName("UsesDependency");
        assertThat(usesDependency).isNotNull();

        Descriptors.FieldDescriptor dependencyField = usesDependency.findFieldByName("dependency");

        assertThat(fileDescriptor.getDependencies()).containsExactly(DEPENDENCY_DESCRIPTOR);
        assertThat(dependencyField).isNotNull();
        assertThat(dependencyField.getMessageType().getFullName())
            .isEqualTo(DEPENDENCY_PACKAGE_NAME + "." + DEPENDENCY_MESSAGE_NAME);
        assertThat(dependencyField.getMessageType().getFile()).isSameAs(DEPENDENCY_DESCRIPTOR);
    }

    public static final class DependencyDescriptorHolder {
        public static final Descriptors.FileDescriptor descriptor = DEPENDENCY_DESCRIPTOR;

        private DependencyDescriptorHolder() {
        }
    }

    private static Descriptors.FileDescriptor createDependencyDescriptor() {
        try {
            return Descriptors.FileDescriptor.buildFrom(
                DescriptorProtos.FileDescriptorProto.newBuilder()
                    .setName(DEPENDENCY_FILE_NAME)
                    .setPackage(DEPENDENCY_PACKAGE_NAME)
                    .setSyntax("proto3")
                    .addMessageType(
                        DescriptorProtos.DescriptorProto.newBuilder()
                            .setName(DEPENDENCY_MESSAGE_NAME)
                            .build()
                    )
                    .build(),
                new Descriptors.FileDescriptor[0]
            );
        } catch (Descriptors.DescriptorValidationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static DescriptorProtos.FileDescriptorProto createDependentFileDescriptorProto() {
        return DescriptorProtos.FileDescriptorProto.newBuilder()
            .setName("descriptors_file_descriptor_test_main.proto")
            .setPackage("kafka.test.main")
            .setSyntax("proto3")
            .addDependency(DEPENDENCY_FILE_NAME)
            .addMessageType(
                DescriptorProtos.DescriptorProto.newBuilder()
                    .setName("UsesDependency")
                    .addField(
                        DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName("dependency")
                            .setNumber(1)
                            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName("." + DEPENDENCY_PACKAGE_NAME + "." + DEPENDENCY_MESSAGE_NAME)
                            .build()
                    )
                    .build()
            )
            .build();
    }

    private static String[] latin1DescriptorData(DescriptorProtos.FileDescriptorProto fileDescriptorProto) {
        return new String[] {new String(fileDescriptorProto.toByteArray(), StandardCharsets.ISO_8859_1)};
    }
}
