/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_java_util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import org.junit.jupiter.api.Test;

/**
 * JSON mapping of protobuf Struct values is the subsystem exercised here.
 *
 * An order service receives a flexible order record from an external API,
 * inspects its fields, and forwards the record as canonical protobuf JSON.
 */
public class JsonFormatStructOrderTest {

    private static final String ORDER_JSON = """
            {
              "orderId": "ORD-2048",
              "priority": 7,
              "expedited": true,
              "warehouse": {
                "region": "eu-west-1",
                "aisle": "C4"
              },
              "labels": ["fragile", "gift"]
            }
            """;

    @Test
    void parsesAndForwardsFlexibleOrderRecord() throws Exception {
        Struct.Builder orderBuilder = Struct.newBuilder();
        JsonFormat.parser().merge(ORDER_JSON, orderBuilder);
        Struct order = orderBuilder.build();

        assertEquals("ORD-2048", stringField(order, "orderId"));
        assertEquals(7.0, order.getFieldsOrThrow("priority").getNumberValue());
        assertTrue(order.getFieldsOrThrow("expedited").getBoolValue());
        assertEquals(
                "eu-west-1",
                order.getFieldsOrThrow("warehouse").getStructValue().getFieldsOrThrow("region")
                        .getStringValue());
        assertEquals(
                "C4",
                order.getFieldsOrThrow("warehouse").getStructValue().getFieldsOrThrow("aisle")
                        .getStringValue());
        assertEquals(2, order.getFieldsOrThrow("labels").getListValue().getValuesCount());

        String forwardedJson = JsonFormat.printer().omittingInsignificantWhitespace().print(order);

        assertEquals(
                "{\"orderId\":\"ORD-2048\",\"priority\":7.0,\"expedited\":true,"
                        + "\"warehouse\":{\"region\":\"eu-west-1\",\"aisle\":\"C4\"},"
                        + "\"labels\":[\"fragile\",\"gift\"]}",
                forwardedJson);
    }

    private static String stringField(Struct message, String fieldName) {
        Value value = message.getFieldsOrThrow(fieldName);
        return value.getStringValue();
    }
}
