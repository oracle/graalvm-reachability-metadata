/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_protobuf_v3_3;

import org.apache.pekko.protobufv3.internal.DescriptorProtos.DescriptorProto;
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto;
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto.Label;
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FieldDescriptorProto.Type;
import org.apache.pekko.protobufv3.internal.DescriptorProtos.FileDescriptorProto;
import org.apache.pekko.protobufv3.internal.Descriptors.DescriptorValidationException;
import org.apache.pekko.protobufv3.internal.Descriptors.FileDescriptor;

public final class GeneratedMessageAnonymous4Descriptor {
    public static final FileDescriptor descriptor = createDescriptor();

    private GeneratedMessageAnonymous4Descriptor() {
    }

    private static FileDescriptor createDescriptor() {
        DescriptorProto hostMessage = DescriptorProto.newBuilder()
                .setName("HostMessage")
                .addExtensionRange(DescriptorProto.ExtensionRange.newBuilder()
                        .setStart(100)
                        .setEnd(101)
                        .build())
                .build();
        FieldDescriptorProto extension = FieldDescriptorProto.newBuilder()
                .setName("host_extension")
                .setNumber(100)
                .setLabel(Label.LABEL_OPTIONAL)
                .setType(Type.TYPE_STRING)
                .setExtendee(".dynamicaccess.generatedmessageanonymous4.HostMessage")
                .build();

        FileDescriptorProto fileProto = FileDescriptorProto.newBuilder()
                .setName("generated_message_anonymous4.proto")
                .setPackage("dynamicaccess.generatedmessageanonymous4")
                .setSyntax("proto2")
                .addMessageType(hostMessage)
                .addExtension(extension)
                .build();
        try {
            return FileDescriptor.buildFrom(fileProto, new FileDescriptor[0]);
        } catch (DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
