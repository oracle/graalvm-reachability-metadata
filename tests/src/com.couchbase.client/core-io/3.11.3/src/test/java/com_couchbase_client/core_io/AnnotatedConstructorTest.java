/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonCreator;
import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonProperty;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotatedConstructorTest {
    @Test
    void mapsApplicationData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Item value = mapper.readValue("""
                {"name":"book","count":4}
                """, Item.class);
        assertEquals("book", value.name);
        assertEquals(4, value.count);
        Label label = mapper.readValue("\"parcel\"", Label.class);
        assertEquals("parcel", label.text);
    }

    @Test
    void restoresConfiguredReaderWithDelegatingConstructor() throws Exception {
        ObjectReader reader = new ObjectMapper().readerFor(Label.class);
        Label before = reader.readValue("\"book\"");
        ObjectReader restored = (ObjectReader) NettySerializationSupport.throughStreams(reader);
        Label after = restored.readValue("\"pencil\"");
        assertEquals("book", before.text);
        assertEquals("pencil", after.text);
    }

    public static class Item {
        public final String name;
        public final int count;

        @JsonCreator
        public Item(@JsonProperty("name") String name, @JsonProperty("count") int count) {
            this.name = name;
            this.count = count;
        }
    }

    public static class Label {
        public final String text;

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public Label(String text) {
            this.text = text;
        }
    }
}
