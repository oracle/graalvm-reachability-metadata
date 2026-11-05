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
import io.trino.jdbc.$internal.jackson.databind.annotation.JsonDeserialize;
import io.trino.jdbc.$internal.jackson.databind.annotation.JsonPOJOBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeanAsArrayBuilderDeserializerTest {
    @Test
    void buildsFromPositionalProperties() throws Exception {
        Entry entry = new ObjectMapper().readValue("[\"desk\",4]", Entry.class);
        assertEquals("desk", entry.name);
        assertEquals(4, entry.count);
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({"name", "count"})
    @JsonDeserialize(builder = EntryBuilder.class)
    public static class Entry {
        public final String name;
        public final int count;

        public Entry(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({"name", "count"})
    public static class EntryBuilder {
        private String name;
        private int count;

        public EntryBuilder name(String name) {
            this.name = name;
            return this;
        }

        public EntryBuilder count(int count) {
            this.count = count;
            return this;
        }

        public Entry build() {
            return new Entry(name, count);
        }
    }
}
