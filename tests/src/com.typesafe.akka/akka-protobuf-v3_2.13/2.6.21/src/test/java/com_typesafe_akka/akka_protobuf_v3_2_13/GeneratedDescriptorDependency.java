/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_2_13;

import akka.protobufv3.internal.DescriptorProtos;
import akka.protobufv3.internal.Descriptors;

public final class GeneratedDescriptorDependency {
    @SuppressWarnings("checkstyle:ConstantName")
    public static final Descriptors.FileDescriptor descriptor = buildDescriptor();

    private GeneratedDescriptorDependency() {
    }

    private static Descriptors.FileDescriptor buildDescriptor() {
        DescriptorProtos.FileDescriptorProto proto = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("coverage/dependency.proto")
                .setPackage("coverage.dependency")
                .addMessageType(DescriptorProtos.DescriptorProto.newBuilder()
                        .setName("DependencyMessage")
                        .build())
                .build();

        try {
            return Descriptors.FileDescriptor.buildFrom(proto, new Descriptors.FileDescriptor[0]);
        } catch (Descriptors.DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
