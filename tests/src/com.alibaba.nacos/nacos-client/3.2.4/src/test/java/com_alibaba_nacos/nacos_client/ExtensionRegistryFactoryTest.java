/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.protobuf.DescriptorProtos;
import com.alibaba.nacos.shaded.com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.alibaba.nacos.shaded.com.google.protobuf.DescriptorProtos.FieldOptions;
import com.alibaba.nacos.shaded.com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.alibaba.nacos.shaded.com.google.protobuf.Descriptors.FieldDescriptor;
import com.alibaba.nacos.shaded.com.google.protobuf.Descriptors.FileDescriptor;
import com.alibaba.nacos.shaded.com.google.protobuf.ExtensionRegistry;
import com.alibaba.nacos.shaded.com.google.protobuf.ExtensionRegistryLite;
import org.junit.jupiter.api.Test;

public class ExtensionRegistryFactoryTest {
    @Test
    void createsFullRegistriesThatRecognizeDescriptorExtensions() throws Exception {
        FileDescriptorProto schema = FileDescriptorProto.newBuilder().setName("metadata-options.proto")
                .setPackage("metadata").addDependency("google/protobuf/descriptor.proto")
                .addExtension(FieldDescriptorProto.newBuilder().setName("owner")
                        .setExtendee(".google.protobuf.FieldOptions").setNumber(50001)
                        .setType(FieldDescriptorProto.Type.TYPE_STRING)
                        .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)).build();
        FileDescriptor file = FileDescriptor.buildFrom(schema, new FileDescriptor[] {DescriptorProtos.getDescriptor()});
        FieldDescriptor owner = file.findExtensionByName("owner");
        ExtensionRegistryLite registry = ExtensionRegistryLite.newInstance();
        ((ExtensionRegistry) registry).add(owner);
        FieldOptions value = FieldOptions.newBuilder().setField(owner, "metadata-test").build();
        FieldOptions restored = FieldOptions.parseFrom(value.toByteArray(), registry);
        assertEquals("metadata-test", restored.getField(owner));
    }
}
