/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_java_util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.util.JsonFormat;
import org.junit.jupiter.api.Test;

/**
 * Forward-compatible protobuf JSON ingestion is the schema synchronization subsystem exercised
 * here.
 *
 * A schema catalog accepts descriptors from newer clients, ignoring fields this version does not
 * know, and emits a response that includes its repeated fields for predictable synchronization.
 */
public class JsonFormatForwardCompatibilityTest {

    private static final String DESCRIPTOR_JSON = """
            {
              "name": "catalog.proto",
              "package": "com.example.catalog",
              "dependency": ["common.proto"],
              "future_option": "preserve-me"
            }
            """;

    @Test
    void synchronizesDescriptorFromNewerClient() throws Exception {
        FileDescriptorProto.Builder descriptorBuilder = FileDescriptorProto.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(DESCRIPTOR_JSON, descriptorBuilder);
        FileDescriptorProto descriptor = descriptorBuilder.build();

        assertEquals("catalog.proto", descriptor.getName());
        assertEquals("com.example.catalog", descriptor.getPackage());
        assertEquals(1, descriptor.getDependencyCount());
        assertEquals("common.proto", descriptor.getDependency(0));
        assertFalse(descriptor.getUnknownFields().hasField(1000));

        String synchronizationResponse =
                JsonFormat.printer()
                        .alwaysPrintFieldsWithNoPresence()
                        .preservingProtoFieldNames()
                        .omittingInsignificantWhitespace()
                        .print(descriptor);

        assertEquals(
                "{\"name\":\"catalog.proto\",\"package\":\"com.example.catalog\","
                        + "\"dependency\":[\"common.proto\"],\"message_type\":[],"
                        + "\"enum_type\":[],\"service\":[],\"extension\":[],"
                        + "\"public_dependency\":[],\"weak_dependency\":[]}",
                synchronizationResponse);
    }
}
