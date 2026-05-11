/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3;

import org.apache.pekko.protobufv3.internal.DescriptorProtos;
import org.apache.pekko.protobufv3.internal.Descriptors;

public final class GeneratedMessageAnonymous4DescriptorProbe {
    public static Descriptors.FileDescriptor descriptor = buildDescriptor();

    private GeneratedMessageAnonymous4DescriptorProbe() {
    }

    private static Descriptors.FileDescriptor buildDescriptor() {
        DescriptorProtos.DescriptorProto extensibleMessage = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("ExtensibleMessage")
                .addExtensionRange(DescriptorProtos.DescriptorProto.ExtensionRange.newBuilder()
                        .setStart(100)
                        .setEnd(536870912))
                .build();

        DescriptorProtos.FileDescriptorProto file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("generated_message_anonymous_4_descriptor_probe.proto")
                .setPackage("coverage.generated_message_anonymous_4")
                .setSyntax("proto2")
                .addMessageType(extensibleMessage)
                .addExtension(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("covered_extension")
                        .setNumber(100)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                        .setExtendee(".coverage.generated_message_anonymous_4.ExtensibleMessage"))
                .build();
        try {
            return Descriptors.FileDescriptor.buildFrom(file, new Descriptors.FileDescriptor[0]);
        } catch (Descriptors.DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
