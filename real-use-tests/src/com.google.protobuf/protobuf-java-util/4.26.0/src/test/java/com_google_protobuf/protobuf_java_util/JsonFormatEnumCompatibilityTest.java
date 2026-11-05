/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_java_util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.util.JsonFormat;
import org.junit.jupiter.api.Test;

/**
 * JSON enum compatibility is the schema evolution subsystem exercised here.
 *
 * A schema catalog accepts descriptors from clients that encode enum values as numbers, then
 * emits the canonical enum names that downstream catalog consumers expect.
 */
public class JsonFormatEnumCompatibilityTest {

    @Test
    void normalizesNumericEnumDescriptorFields() throws Exception {
        String descriptorJson = """
                {
                  "name": "status",
                  "number": 2,
                  "label": 1,
                  "type": 14,
                  "json_name": "status"
                }
                """;
        FieldDescriptorProto.Builder descriptorBuilder = FieldDescriptorProto.newBuilder();
        JsonFormat.parser().merge(descriptorJson, descriptorBuilder);
        FieldDescriptorProto descriptor = descriptorBuilder.build();

        assertEquals("status", descriptor.getName());
        assertEquals(2, descriptor.getNumber());
        assertEquals(FieldDescriptorProto.Label.LABEL_OPTIONAL, descriptor.getLabel());
        assertEquals(FieldDescriptorProto.Type.TYPE_ENUM, descriptor.getType());
        assertEquals("status", descriptor.getJsonName());

        String catalogJson =
                JsonFormat.printer().omittingInsignificantWhitespace().print(descriptor);

        assertEquals(
                "{\"name\":\"status\",\"number\":2,\"label\":\"LABEL_OPTIONAL\","
                        + "\"type\":\"TYPE_ENUM\",\"jsonName\":\"status\"}",
                catalogJson);
    }
}
