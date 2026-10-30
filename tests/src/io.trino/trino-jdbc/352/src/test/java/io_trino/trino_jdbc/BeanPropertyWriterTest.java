/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.annotation.JsonFormat;
import io.trino.jdbc.$internal.jackson.annotation.JsonPropertyOrder;
import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeanPropertyWriterTest {
    @Test
    void writesFieldsAndGettersAsObjectsAndArrays() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String object = mapper.writeValueAsString(new Reading());
        assertEquals("station", mapper.readTree(object).get("name").asText());
        assertEquals(18, mapper.readTree(object).get("value").asInt());
        assertEquals("[\"station\",18]", mapper.writeValueAsString(new ArrayReading()));
    }

    @JsonPropertyOrder({"name", "value"})
    public static class Reading {
        public String name = "station";

        public int getValue() {
            return 18;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    public static class ArrayReading extends Reading {
    }
}
