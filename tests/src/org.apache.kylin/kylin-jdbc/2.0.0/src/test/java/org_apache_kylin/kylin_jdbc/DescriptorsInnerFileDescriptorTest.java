/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.nio.charset.StandardCharsets;

import org.apache.kylin.jdbc.shaded.com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import org.apache.kylin.jdbc.shaded.com.google.protobuf.Descriptors.DescriptorValidationException;
import org.apache.kylin.jdbc.shaded.com.google.protobuf.Descriptors.FileDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DescriptorsInnerFileDescriptorTest {
    @Test
    void buildsDescriptorWithDependencyDiscoveredFromItsOuterClassLoader() {
        FileDescriptorProto rootProto = FileDescriptorProto.newBuilder()
                .setName("root.proto")
                .addDependency(DependencyDescriptor.descriptor.getName())
                .build();
        FileDescriptor[] assigned = new FileDescriptor[1];

        FileDescriptor.internalBuildGeneratedFileFrom(
                new String[] {new String(rootProto.toByteArray(), StandardCharsets.ISO_8859_1)},
                DescriptorsInnerFileDescriptorTest.class,
                new String[] {DependencyDescriptor.class.getName()},
                new String[] {DependencyDescriptor.descriptor.getName()},
                descriptor -> {
                    assigned[0] = descriptor;
                    return null;
                });

        assertThat(assigned[0].getName()).isEqualTo("root.proto");
        assertThat(assigned[0].getDependencies()).containsExactly(DependencyDescriptor.descriptor);
    }

    public static class DependencyDescriptor {
        public static final FileDescriptor descriptor = createDescriptor();

        private static FileDescriptor createDescriptor() {
            try {
                return FileDescriptor.buildFrom(
                        FileDescriptorProto.newBuilder().setName("dependency.proto").build(),
                        new FileDescriptor[0]);
            } catch (DescriptorValidationException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
