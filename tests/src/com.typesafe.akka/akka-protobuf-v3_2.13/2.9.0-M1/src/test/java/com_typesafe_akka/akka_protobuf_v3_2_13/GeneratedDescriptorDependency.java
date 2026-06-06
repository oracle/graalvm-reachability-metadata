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
                .addMessageType(DescriptorProtos.DescriptorProto.newBuilder()
                        .setName("ExtensibleMessage")
                        .addExtensionRange(DescriptorProtos.DescriptorProto.ExtensionRange.newBuilder()
                                .setStart(100)
                                .setEnd(536870912)
                                .build())
                        .build())
                .addExtension(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("covered_extension")
                        .setNumber(100)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                        .setExtendee(".coverage.dependency.ExtensibleMessage")
                        .build())
                .build();

        try {
            return Descriptors.FileDescriptor.buildFrom(proto, new Descriptors.FileDescriptor[0]);
        } catch (Descriptors.DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
