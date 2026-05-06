/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_3;

import akka.protobufv3.internal.DescriptorProtos;
import akka.protobufv3.internal.Descriptors;

/**
 * Minimal generated-code-shaped dependency exposing the legacy public descriptor field.
 */
public final class FileDescriptorReflectiveDependency {
    @SuppressWarnings("checkstyle:ConstantName")
    public static final Descriptors.FileDescriptor descriptor = createDescriptor();

    private FileDescriptorReflectiveDependency() {
    }

    private static Descriptors.FileDescriptor createDescriptor() {
        DescriptorProtos.FileDescriptorProto proto = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("reflective_dependency.proto")
                .setPackage("coverage.dynamic")
                .addMessageType(DescriptorProtos.DescriptorProto.newBuilder()
                        .setName("DependencyMessage")
                        .addExtensionRange(DescriptorProtos.DescriptorProto.ExtensionRange.newBuilder()
                                .setStart(100)
                                .setEnd(101)
                                .build())
                        .build())
                .addExtension(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("probe_extension")
                        .setNumber(100)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
                        .setExtendee(".coverage.dynamic.DependencyMessage")
                        .build())
                .build();
        try {
            return Descriptors.FileDescriptor.buildFrom(proto, new Descriptors.FileDescriptor[0]);
        } catch (Descriptors.DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
