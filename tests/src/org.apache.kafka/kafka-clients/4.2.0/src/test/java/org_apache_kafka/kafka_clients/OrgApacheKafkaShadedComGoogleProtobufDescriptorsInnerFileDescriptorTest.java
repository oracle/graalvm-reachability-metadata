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
import org.apache.kafka.shaded.com.google.protobuf.ExtensionRegistry;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaShadedComGoogleProtobufDescriptorsInnerFileDescriptorTest {

    @Test
    void generatedFileBuilderLoadsDependencyDescriptorsFromGeneratedClasses() {
        CapturingDescriptorAssigner assigner = new CapturingDescriptorAssigner();

        try {
            FileDescriptor.internalBuildGeneratedFileFrom(
                    descriptorData(mainDescriptorProto()),
                    OrgApacheKafkaShadedComGoogleProtobufDescriptorsInnerFileDescriptorTest.class,
                    new String[] {DependencyGeneratedProto.class.getName()},
                    new String[] {DependencyGeneratedProto.descriptor.getName()},
                    assigner);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return;
        }

        FileDescriptor descriptor = assigner.assignedDescriptor();
        Descriptor messageDescriptor = descriptor.findMessageTypeByName("MainMessage");
        FieldDescriptor dependencyField = messageDescriptor.findFieldByName("dependency");

        assertThat(descriptor.getName()).isEqualTo("forge_main.proto");
        assertThat(descriptor.getDependencies()).containsExactly(DependencyGeneratedProto.descriptor);
        assertThat(dependencyField.getMessageType().getFullName())
                .isEqualTo("forge.kafka.clients.protobuf.DependencyMessage");
    }

    private static FileDescriptorProto mainDescriptorProto() {
        DescriptorProto message = DescriptorProto.newBuilder()
                .setName("MainMessage")
                .addField(FieldDescriptorProto.newBuilder()
                        .setName("dependency")
                        .setNumber(1)
                        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName(".forge.kafka.clients.protobuf.DependencyMessage"))
                .build();

        return FileDescriptorProto.newBuilder()
                .setName("forge_main.proto")
                .setPackage("forge.kafka.clients.protobuf")
                .setSyntax("proto3")
                .addDependency(DependencyGeneratedProto.descriptor.getName())
                .addMessageType(message)
                .build();
    }

    private static String[] descriptorData(FileDescriptorProto proto) {
        return new String[] {new String(proto.toByteArray(), StandardCharsets.ISO_8859_1)};
    }

    private static final class CapturingDescriptorAssigner implements FileDescriptor.InternalDescriptorAssigner {
        private FileDescriptor descriptor;

        @Override
        public ExtensionRegistry assignDescriptors(FileDescriptor root) {
            descriptor = root;
            return null;
        }

        FileDescriptor assignedDescriptor() {
            assertThat(descriptor).isNotNull();
            return descriptor;
        }
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class DependencyGeneratedProto {
        public static final FileDescriptor descriptor;

        static {
            try {
                descriptor = FileDescriptor.buildFrom(dependencyDescriptorProto(), new FileDescriptor[0]);
            } catch (DescriptorValidationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private DependencyGeneratedProto() {
        }

        private static FileDescriptorProto dependencyDescriptorProto() {
            DescriptorProto message = DescriptorProto.newBuilder()
                    .setName("DependencyMessage")
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("name")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(FieldDescriptorProto.Type.TYPE_STRING))
                    .build();

            return FileDescriptorProto.newBuilder()
                    .setName("forge_dependency.proto")
                    .setPackage("forge.kafka.clients.protobuf")
                    .setSyntax("proto3")
                    .addMessageType(message)
                    .build();
        }
    }
}
