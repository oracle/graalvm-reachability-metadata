/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_java_util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.FieldMask;
import com.google.protobuf.util.FieldMaskUtil;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Field-mask replacement policy for schema rollouts is the schema deployment subsystem exercised
 * here.
 *
 * A deployment service applies a selected schema update while choosing whether repeated imports
 * and nested compiler options should replace the existing values or be merged into them.
 */
public class FieldMaskMergePolicyTest {

    @Test
    void replacesSelectedRepeatedAndNestedSchemaFields() {
        FileDescriptorProto current =
                FileDescriptorProto.newBuilder()
                        .setName("orders.proto")
                        .addDependency("common.proto")
                        .setOptions(
                                FileOptions.newBuilder()
                                        .setJavaPackage("com.example.orders")
                                        .setJavaMultipleFiles(false)
                                        .build())
                        .build();
        FileDescriptorProto rollout =
                FileDescriptorProto.newBuilder()
                        .addDependency("billing.proto")
                        .setOptions(
                                FileOptions.newBuilder()
                                        .setJavaPackage("com.acme.orders")
                                        .setJavaMultipleFiles(true)
                                        .build())
                        .build();
        FieldMask selected = FieldMaskUtil.fromString("dependency,options");
        FileDescriptorProto.Builder merged = current.toBuilder();

        FieldMaskUtil.MergeOptions replacementPolicy =
                new FieldMaskUtil.MergeOptions()
                        .setReplaceRepeatedFields(true)
                        .setReplaceMessageFields(true);
        FieldMaskUtil.merge(selected, rollout, merged, replacementPolicy);

        FileDescriptorProto deployed = merged.build();
        assertEquals(List.of("billing.proto"), deployed.getDependencyList());
        assertEquals("com.acme.orders", deployed.getOptions().getJavaPackage());
        assertEquals(true, deployed.getOptions().getJavaMultipleFiles());
        assertEquals("orders.proto", deployed.getName());
    }
}
