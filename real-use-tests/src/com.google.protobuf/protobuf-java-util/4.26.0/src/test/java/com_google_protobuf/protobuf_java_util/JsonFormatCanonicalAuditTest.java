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
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import org.junit.jupiter.api.Test;

/**
 * Canonical protobuf JSON serialization is the audit-signing subsystem exercised here.
 *
 * An audit service serializes descriptor metadata and dynamic labels into stable JSON before
 * calculating a cache key, so equivalent records must use sorted map keys and proto field names.
 */
public class JsonFormatCanonicalAuditTest {

    @Test
    void producesStableJsonForAuditRecords() throws Exception {
        Struct labels =
                Struct.newBuilder()
                        .putFields(
                                "z-region",
                                Value.newBuilder().setStringValue("eu").build())
                        .putFields(
                                "a-tier",
                                Value.newBuilder().setStringValue("gold").build())
                        .build();
        FileDescriptorProto descriptor =
                FileDescriptorProto.newBuilder()
                        .setName("audit.proto")
                        .setOptions(
                                FileOptions.newBuilder().setJavaPackage("com.example.audit").build())
                        .setSyntax("proto3")
                        .build();

        JsonFormat.Printer printer =
                JsonFormat.printer()
                        .sortingMapKeys()
                        .preservingProtoFieldNames()
                        .omittingInsignificantWhitespace();

        String labelsJson = printer.print(labels);
        String descriptorJson = printer.print(descriptor);

        assertEquals("{\"a-tier\":\"gold\",\"z-region\":\"eu\"}", labelsJson);
        assertEquals(
                "{\"name\":\"audit.proto\",\"options\":{\"java_package\":\"com.example.audit\"},"
                        + "\"syntax\":\"proto3\"}",
                descriptorJson);
    }
}
