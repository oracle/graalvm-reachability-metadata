/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.google.protobuf.DescriptorProtos;
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

public class GeneratedMessageTest {
    @Test
    void convertsAnEnumExtensionDefaultThroughItsGeneratedAccessors() {
        GeneratedExtension<?, ?> extension = GeneratedMessage.newFileScopedGeneratedExtension(
                Type.class, null, EnumExtensionDescriptor.class.getName(), "enum_tag");

        assertThat(extension.getDefaultValue()).isEqualTo(Type.TYPE_DOUBLE);
    }

    public static class EnumExtensionDescriptor {
        public static final FileDescriptor descriptor = createDescriptor();

        private static FileDescriptor createDescriptor() {
            FieldDescriptorProto extension = FieldDescriptorProto.newBuilder()
                    .setName("enum_tag")
                    .setNumber(50000)
                    .setLabel(Label.LABEL_OPTIONAL)
                    .setType(Type.TYPE_ENUM)
                    .setTypeName(".google.protobuf.FieldDescriptorProto.Type")
                    .setExtendee(".google.protobuf.FileOptions")
                    .build();
            FileDescriptorProto proto = FileDescriptorProto.newBuilder()
                    .setName("enum_extension.proto")
                    .setPackage("sample")
                    .addDependency(DescriptorProtos.getDescriptor().getName())
                    .addExtension(extension)
                    .build();
            try {
                return FileDescriptor.buildFrom(proto, new FileDescriptor[] {DescriptorProtos.getDescriptor()});
            } catch (DescriptorValidationException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
