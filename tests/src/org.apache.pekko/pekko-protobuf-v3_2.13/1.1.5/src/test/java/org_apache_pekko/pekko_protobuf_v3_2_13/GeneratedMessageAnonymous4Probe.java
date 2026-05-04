/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_2_13;

import org.apache.pekko.protobufv3.internal.DescriptorProtos;
import org.apache.pekko.protobufv3.internal.Descriptors;

public final class GeneratedMessageAnonymous4Probe {
    public static Descriptors.FileDescriptor descriptor = buildDescriptor();

    private GeneratedMessageAnonymous4Probe() {
    }

    private static Descriptors.FileDescriptor buildDescriptor() {
        DescriptorProtos.DescriptorProto extendableMessage = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("GeneratedMessageAnonymous4ProbeMessage")
                .addExtensionRange(DescriptorProtos.DescriptorProto.ExtensionRange.newBuilder()
                        .setStart(100)
                        .setEnd(200))
                .build();

        DescriptorProtos.FieldDescriptorProto extension = DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName("probe_extension")
                .setNumber(100)
                .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                .setExtendee(".coverage.GeneratedMessageAnonymous4ProbeMessage")
                .build();

        DescriptorProtos.FileDescriptorProto file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("generated_message_anonymous4_probe.proto")
                .setPackage("coverage")
                .setSyntax("proto2")
                .addMessageType(extendableMessage)
                .addExtension(extension)
                .build();
        try {
            return Descriptors.FileDescriptor.buildFrom(file, new Descriptors.FileDescriptor[0]);
        } catch (Descriptors.DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
