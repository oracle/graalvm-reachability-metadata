/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3;

import org.apache.pekko.protobufv3.internal.DescriptorProtos.DescriptorProto;
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FileDescriptorProto;
import org.apache.pekko.protobufv3.internal.Descriptors.DescriptorValidationException;
import org.apache.pekko.protobufv3.internal.Descriptors.FileDescriptor;

public final class GeneratedDependencyDescriptor {
    public static final FileDescriptor descriptor = createDescriptor();

    private GeneratedDependencyDescriptor() {
    }

    private static FileDescriptor createDescriptor() {
        FileDescriptorProto dependencyProto = FileDescriptorProto.newBuilder()
                .setName("dynamic_access_dependency.proto")
                .setPackage("dynamicaccess.dependency")
                .setSyntax("proto3")
                .addMessageType(DescriptorProto.newBuilder()
                        .setName("DependencyMessage")
                        .build())
                .build();
        try {
            return FileDescriptor.buildFrom(dependencyProto, new FileDescriptor[0]);
        } catch (DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
