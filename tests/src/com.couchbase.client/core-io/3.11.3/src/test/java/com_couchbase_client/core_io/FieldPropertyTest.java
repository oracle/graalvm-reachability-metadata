/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FieldPropertyTest {
    @Test
    void mapsApplicationData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Fields value = mapper.readValue("""
                {"name":"Ada","count":9}
                """, Fields.class);
        assertEquals("Ada", value.name);
        assertEquals(9, value.count);
        mapper.readerForUpdating(value).readValue("""
                {"count":12}
                """);
        assertEquals("Ada", value.name);
        assertEquals(12, value.count);
    }

    @Test
    void populatesBuilderFieldsBeforeBuilding() throws Exception {
        BuiltDocument document = new ObjectMapper().readValue("""
                {"name":"Atlas","count":12}
                """, BuiltDocument.class);
        assertEquals("Atlas", document.name);
        assertEquals(12, document.count);
    }

    @JsonDeserialize(builder = DocumentBuilder.class)
    public static class BuiltDocument {
        public final String name;
        public final int count;

        public BuiltDocument(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class DocumentBuilder {
        public String name;
        public int count;

        public BuiltDocument build() {
            return new BuiltDocument(name, count);
        }
    }

    public static class Fields {
        public String name;
        public int count;
    }
}
