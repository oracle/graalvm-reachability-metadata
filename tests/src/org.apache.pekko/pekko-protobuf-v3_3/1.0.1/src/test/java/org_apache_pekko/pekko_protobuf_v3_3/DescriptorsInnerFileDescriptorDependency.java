/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3;

import java.nio.charset.StandardCharsets;

import org.apache.pekko.protobufv3.internal.DescriptorProtos;
import org.apache.pekko.protobufv3.internal.Descriptors;

public final class DescriptorsInnerFileDescriptorDependency {
    private static final String PROTO_NAME = "descriptors_inner_file_descriptor_dependency.proto";

    public static final Descriptors.FileDescriptor descriptor = buildDescriptor();

    private DescriptorsInnerFileDescriptorDependency() {
    }

    private static Descriptors.FileDescriptor buildDescriptor() {
        final DescriptorProtos.FileDescriptorProto proto = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName(PROTO_NAME)
                .setPackage("descriptors_inner_file_descriptor")
                .setSyntax("proto3")
                .addMessageType(
                        DescriptorProtos.DescriptorProto.newBuilder()
                                .setName("DependencyMessage")
                                .addField(
                                        DescriptorProtos.FieldDescriptorProto.newBuilder()
                                                .setName("value")
                                                .setNumber(1)
                                                .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                                                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                                                .build())
                                .build())
                .build();
        final String[] descriptorData = {new String(proto.toByteArray(), StandardCharsets.ISO_8859_1)};
        return Descriptors.FileDescriptor.internalBuildGeneratedFileFrom(
                descriptorData,
                new Descriptors.FileDescriptor[0]);
    }
}
