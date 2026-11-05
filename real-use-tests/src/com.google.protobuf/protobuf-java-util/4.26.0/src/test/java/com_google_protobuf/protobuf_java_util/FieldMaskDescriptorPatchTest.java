/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_java_util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.FieldMask;
import com.google.protobuf.util.FieldMaskUtil;
import org.junit.jupiter.api.Test;

/**
 * Field-mask based partial updates of protobuf descriptors are the subsystem exercised here.
 *
 * A schema registry accepts a descriptor patch, applies only the fields named by its
 * field mask, and returns a response containing the fields the caller requested.
 */
public class FieldMaskDescriptorPatchTest {

    @Test
    void appliesDescriptorPatchAndTrimsResponse() {
        FileDescriptorProto existing =
                FileDescriptorProto.newBuilder()
                        .setName("inventory.proto")
                        .setPackage("com.example.inventory")
                        .setSyntax("proto2")
                        .build();
        FileDescriptorProto patch =
                FileDescriptorProto.newBuilder()
                        .setName("ignored.proto")
                        .setPackage("com.example.catalog")
                        .setSyntax("proto3")
                        .build();

        FieldMask updateMask = FieldMaskUtil.fromString("package,syntax");
        FileDescriptorProto.Builder merged = existing.toBuilder();
        FieldMaskUtil.merge(updateMask, patch, merged);
        FileDescriptorProto updated = merged.build();

        assertEquals("inventory.proto", updated.getName());
        assertEquals("com.example.catalog", updated.getPackage());
        assertEquals("proto3", updated.getSyntax());
        assertTrue(FieldMaskUtil.isValid(FileDescriptorProto.getDescriptor(), updateMask));
        assertEquals("package,syntax", FieldMaskUtil.toJsonString(updateMask));

        FieldMask responseMask = FieldMaskUtil.fromString("name,package");
        FileDescriptorProto response = FieldMaskUtil.trim(responseMask, updated);

        assertEquals("inventory.proto", response.getName());
        assertEquals("com.example.catalog", response.getPackage());
        assertFalse(response.hasSyntax());
    }
}
