/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors;
import org.apache.kafka.shaded.com.google.protobuf.DynamicMessage;
import org.apache.kafka.shaded.com.google.protobuf.GeneratedMessage;
import org.apache.kafka.shaded.com.google.protobuf.Message;
import org.junit.jupiter.api.Test;

public class GeneratedMessageAnonymous4Test {
    private static final String EXTENSION_NAME = "covered_extension";
    public static final Descriptors.FileDescriptor descriptor = buildDescriptor();

    @Test
    void resolvesFileScopedGeneratedExtensionDescriptorFromOuterClassName() {
        DynamicMessage extensionDefaultInstance = DynamicMessage.getDefaultInstance(
                descriptor.findMessageTypeByName("ExtensionValue"));
        GeneratedMessage.GeneratedExtension<Message, DynamicMessage> extension =
                GeneratedMessage.<Message, DynamicMessage>newFileScopedGeneratedExtension(
                        DynamicMessage.class,
                        extensionDefaultInstance,
                        GeneratedMessageAnonymous4Test.class.getName(),
                        EXTENSION_NAME);

        Descriptors.FieldDescriptor fieldDescriptor = extension.getDescriptor();

        assertSame(descriptor.findExtensionByName(EXTENSION_NAME), fieldDescriptor);
        assertEquals(EXTENSION_NAME, fieldDescriptor.getName());
        assertEquals(100, extension.getNumber());
        assertSame(extensionDefaultInstance, extension.getMessageDefaultInstance());
    }

    private static Descriptors.FileDescriptor buildDescriptor() {
        try {
            return Descriptors.FileDescriptor.buildFrom(fileDescriptorProto(), new Descriptors.FileDescriptor[0]);
        } catch (Descriptors.DescriptorValidationException exception) {
            throw new IllegalStateException("Unable to build extension descriptor", exception);
        }
    }

    private static DescriptorProtos.FileDescriptorProto fileDescriptorProto() {
        DescriptorProtos.DescriptorProto coverageContainer = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("CoverageContainer")
                .addExtensionRange(DescriptorProtos.DescriptorProto.ExtensionRange.newBuilder()
                        .setStart(100)
                        .setEnd(101))
                .build();
        DescriptorProtos.DescriptorProto extensionValue = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("ExtensionValue")
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("name")
                        .setNumber(1)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL))
                .build();
        DescriptorProtos.FieldDescriptorProto extension = DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName(EXTENSION_NAME)
                .setNumber(100)
                .setExtendee(".generated_message_anonymous4.CoverageContainer")
                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                .setTypeName(".generated_message_anonymous4.ExtensionValue")
                .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .build();

        return DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("generated_message_anonymous4.proto")
                .setPackage("generated_message_anonymous4")
                .setSyntax("proto2")
                .addMessageType(coverageContainer)
                .addMessageType(extensionValue)
                .addExtension(extension)
                .build();
    }
}
