/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonFormat;
import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeanPropertyWriterTest {
    @Test
    void mapsApplicationData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Document document = new Document();
        assertEquals(mapper.readTree("""
                {"id":7,"name":"Ada"}
                """), mapper.readTree(mapper.writeValueAsString(document)));
        assertEquals(mapper.readTree("""
                [7,"Ada"]
                """), mapper.readTree(mapper.writeValueAsString(new Row())));
    }

    public static class Document {
        public int id = 7;

        public String getName() {
            return "Ada";
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({"id", "name"})
    public static class Row extends Document {
    }
}
