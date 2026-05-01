/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_protobuf_3_7;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.hadoop.thirdparty.protobuf.DescriptorProtos;
import org.apache.hadoop.thirdparty.protobuf.Descriptors;
import org.apache.hadoop.thirdparty.protobuf.ExtensionRegistry;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class DescriptorsInnerFileDescriptorTest {
    private static final String DEPENDENCY_PROTO_NAME = "coverage/dependency.proto";
    private static final String MAIN_PROTO_NAME = "coverage/main.proto";

    @Test
    void generatedFileBuilderLoadsDependencyDescriptorsFromGeneratedClassNames() {
        CapturingDescriptorAssigner assigner = new CapturingDescriptorAssigner();

        try {
            Descriptors.FileDescriptor.internalBuildGeneratedFileFrom(
                    descriptorData(mainProto()),
                    DescriptorsInnerFileDescriptorTest.class,
                    new String[] {DependencyDescriptorHolder.class.getName()},
                    new String[] {DEPENDENCY_PROTO_NAME},
                    assigner
            );
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return;
        }

        Descriptors.FileDescriptor descriptor = assigner.assignedDescriptor();
        assertThat(descriptor.getName()).isEqualTo(MAIN_PROTO_NAME);
        assertThat(descriptor.getDependencies()).containsExactly(DependencyDescriptorHolder.descriptor);
        assertThat(descriptor.findMessageTypeByName("MainMessage")
                .findFieldByName("dependency")
                .getMessageType()
                .getFullName()).isEqualTo("coverage.DependencyMessage");
    }

    private static String[] descriptorData(DescriptorProtos.FileDescriptorProto proto) {
        return new String[] {new String(proto.toByteArray(), StandardCharsets.ISO_8859_1)};
    }

    private static DescriptorProtos.FileDescriptorProto dependencyProto() {
        return DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName(DEPENDENCY_PROTO_NAME)
                .setPackage("coverage")
                .addMessageType(DescriptorProtos.DescriptorProto.newBuilder()
                        .setName("DependencyMessage")
                        .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                                .setName("id")
                                .setNumber(1)
                                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)))
                .build();
    }

    private static DescriptorProtos.FileDescriptorProto mainProto() {
        return DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName(MAIN_PROTO_NAME)
                .setPackage("coverage")
                .addDependency(DEPENDENCY_PROTO_NAME)
                .addMessageType(DescriptorProtos.DescriptorProto.newBuilder()
                        .setName("MainMessage")
                        .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                                .setName("dependency")
                                .setNumber(1)
                                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                                .setTypeName(".coverage.DependencyMessage")))
                .build();
    }

    private static Descriptors.FileDescriptor buildFileDescriptor(DescriptorProtos.FileDescriptorProto proto) {
        try {
            return Descriptors.FileDescriptor.buildFrom(proto, new Descriptors.FileDescriptor[0]);
        } catch (Descriptors.DescriptorValidationException exception) {
            throw new IllegalStateException("Test descriptor should be valid", exception);
        }
    }

    public static final class DependencyDescriptorHolder {
        public static final Descriptors.FileDescriptor descriptor = buildFileDescriptor(dependencyProto());

        private DependencyDescriptorHolder() {
        }
    }

    private static final class CapturingDescriptorAssigner
            implements Descriptors.FileDescriptor.InternalDescriptorAssigner {
        private Descriptors.FileDescriptor descriptor;

        @Override
        public ExtensionRegistry assignDescriptors(Descriptors.FileDescriptor root) {
            descriptor = root;
            return null;
        }

        Descriptors.FileDescriptor assignedDescriptor() {
            assertThat(descriptor).isNotNull();
            return descriptor;
        }
    }
}
