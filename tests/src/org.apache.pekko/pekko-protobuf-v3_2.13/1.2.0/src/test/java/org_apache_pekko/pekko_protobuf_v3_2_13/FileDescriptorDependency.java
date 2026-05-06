/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13;

import org.apache.pekko.protobufv3.internal.DescriptorProtos;
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto.Label;
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto.Type;
import org.apache.pekko.protobufv3.internal.Descriptors;

public final class FileDescriptorDependency {
    public static final Descriptors.FileDescriptor descriptor = buildDescriptor();

    private FileDescriptorDependency() {
    }

    private static Descriptors.FileDescriptor buildDescriptor() {
        DescriptorProtos.DescriptorProto message = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("DependencyMessage")
                .addField(field("id", 1, Label.LABEL_OPTIONAL, Type.TYPE_STRING))
                .build();

        DescriptorProtos.FileDescriptorProto file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("file_descriptor_dependency.proto")
                .setPackage("coverage")
                .setSyntax("proto3")
                .addMessageType(message)
                .build();

        try {
            return Descriptors.FileDescriptor.buildFrom(file, new Descriptors.FileDescriptor[0]);
        } catch (Descriptors.DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static DescriptorProtos.FieldDescriptorProto.Builder field(
            String name,
            int number,
            Label label,
            Type type) {
        return DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName(name)
                .setNumber(number)
                .setLabel(label)
                .setType(type);
    }
}
