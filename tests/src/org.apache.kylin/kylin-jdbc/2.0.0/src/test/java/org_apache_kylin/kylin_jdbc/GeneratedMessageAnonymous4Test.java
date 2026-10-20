/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.google.protobuf.DescriptorProtos.DescriptorProto;
import org.apache.kylin.jdbc.shaded.com.google.protobuf.DescriptorProtos.DescriptorProto.ExtensionRange;
import org.apache.kylin.jdbc.shaded.com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import org.apache.kylin.jdbc.shaded.com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import org.apache.kylin.jdbc.shaded.com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import org.apache.kylin.jdbc.shaded.com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import org.apache.kylin.jdbc.shaded.com.google.protobuf.Descriptors.DescriptorValidationException;
import org.apache.kylin.jdbc.shaded.com.google.protobuf.Descriptors.FileDescriptor;
import org.apache.kylin.jdbc.shaded.com.google.protobuf.GeneratedMessage;
import org.apache.kylin.jdbc.shaded.com.google.protobuf.GeneratedMessage.GeneratedExtension;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GeneratedMessageAnonymous4Test {
    @Test
    void resolvesAFileScopedExtensionFromItsDescriptorHolder() {
        GeneratedExtension<?, ?> extension = newTextExtension();

        assertThat(extension.getDescriptor()).isSameAs(ExtensionDescriptor.descriptor.findExtensionByName("tag"));
        assertThat(extension.getNumber()).isEqualTo(100);
    }

    public static GeneratedExtension<?, ?> newTextExtension() {
        return GeneratedMessage.newFileScopedGeneratedExtension(
                String.class, null, ExtensionDescriptor.class.getName(), "tag");
    }

    public static class ExtensionDescriptor {
        public static final FileDescriptor descriptor = createDescriptor();

        private static FileDescriptor createDescriptor() {
            DescriptorProto host = DescriptorProto.newBuilder()
                    .setName("Host")
                    .addExtensionRange(ExtensionRange.newBuilder().setStart(100).setEnd(101))
                    .build();
            FieldDescriptorProto extension = FieldDescriptorProto.newBuilder()
                    .setName("tag")
                    .setNumber(100)
                    .setLabel(Label.LABEL_OPTIONAL)
                    .setType(Type.TYPE_STRING)
                    .setExtendee(".sample.Host")
                    .build();
            FileDescriptorProto proto = FileDescriptorProto.newBuilder()
                    .setName("extension.proto")
                    .setPackage("sample")
                    .addMessageType(host)
                    .addExtension(extension)
                    .build();
            try {
                return FileDescriptor.buildFrom(proto, new FileDescriptor[0]);
            } catch (DescriptorValidationException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
