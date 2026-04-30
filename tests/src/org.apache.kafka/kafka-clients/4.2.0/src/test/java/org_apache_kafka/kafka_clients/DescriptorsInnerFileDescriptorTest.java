/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.shaded.com.google.protobuf.DescriptorProtos;
import org.apache.kafka.shaded.com.google.protobuf.Descriptors;
import org.junit.jupiter.api.Test;

public class DescriptorsInnerFileDescriptorTest {
    @Test
    void buildsGeneratedFileDescriptorByLoadingDependencyDescriptorClass() {
        Descriptors.FileDescriptor descriptor = Descriptors.FileDescriptor.internalBuildGeneratedFileFrom(
                descriptorData(mainProto()),
                DescriptorsInnerFileDescriptorTest.class,
                new String[] {DependencyProto.class.getName()},
                new String[] {"dependency.proto"});

        Descriptors.Descriptor messageDescriptor = descriptor.findMessageTypeByName("UsesDependency");
        assertNotNull(messageDescriptor);
        assertEquals(
                DependencyProto.descriptor.findMessageTypeByName("DependencyMessage"),
                messageDescriptor.findFieldByName("dependency").getMessageType());
    }

    public static final class DependencyProto {
        public static final Descriptors.FileDescriptor descriptor = buildDependencyDescriptor();

        private DependencyProto() {
        }
    }

    private static Descriptors.FileDescriptor buildDependencyDescriptor() {
        try {
            return Descriptors.FileDescriptor.buildFrom(dependencyProto(), new Descriptors.FileDescriptor[0]);
        } catch (Descriptors.DescriptorValidationException exception) {
            throw new IllegalStateException("Unable to build dependency descriptor", exception);
        }
    }

    private static DescriptorProtos.FileDescriptorProto dependencyProto() {
        DescriptorProtos.DescriptorProto dependencyMessage = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("DependencyMessage")
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("value")
                        .setNumber(1)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL))
                .build();
        return DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("dependency.proto")
                .setPackage("coverage")
                .setSyntax("proto3")
                .addMessageType(dependencyMessage)
                .build();
    }

    private static DescriptorProtos.FileDescriptorProto mainProto() {
        DescriptorProtos.DescriptorProto message = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("UsesDependency")
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("dependency")
                        .setNumber(1)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName(".coverage.DependencyMessage")
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL))
                .build();
        return DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("uses_dependency.proto")
                .setPackage("coverage")
                .setSyntax("proto3")
                .addDependency("dependency.proto")
                .addMessageType(message)
                .build();
    }

    private static String[] descriptorData(DescriptorProtos.FileDescriptorProto descriptorProto) {
        return new String[] {new String(descriptorProto.toByteArray(), StandardCharsets.ISO_8859_1)};
    }
}
