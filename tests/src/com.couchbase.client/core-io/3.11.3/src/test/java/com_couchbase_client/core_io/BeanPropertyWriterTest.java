/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonFormat;
import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.JsonNode;
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

    @Test
    void writesObjectIdentityFromFieldsAndGetters() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        FieldNode fieldNode = new FieldNode();
        fieldNode.next = fieldNode;
        JsonNode fieldJson = mapper.readTree(mapper.writeValueAsString(fieldNode));
        assertEquals(23, fieldJson.get("id").asInt());
        assertEquals(23, fieldJson.get("next").asInt());

        GetterNode getterNode = new GetterNode();
        getterNode.next = getterNode;
        JsonNode getterJson = mapper.readTree(mapper.writeValueAsString(getterNode));
        assertEquals(41, getterJson.get("id").asInt());
        assertEquals(41, getterJson.get("next").asInt());
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    public static class FieldNode {
        public int id = 23;
        public FieldNode next;
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    public static class GetterNode {
        public GetterNode next;

        public int getId() {
            return 41;
        }
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
