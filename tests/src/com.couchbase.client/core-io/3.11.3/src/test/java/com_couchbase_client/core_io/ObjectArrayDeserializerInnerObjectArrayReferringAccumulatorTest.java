/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ObjectArrayDeserializerInnerObjectArrayReferringAccumulatorTest {
    @Test
    void resolvesArrayReferencesFromLaterDefinitions() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Graph graph = mapper.readValue("""
                {"nodes":[2,1],"definitions":[{"id":1,"name":"first"},{"id":2,"name":"second"}]}
                """, Graph.class);
        assertEquals(2, graph.nodes.length);
        assertSame(graph.definitions.get(1), graph.nodes[0]);
        assertSame(graph.definitions.get(0), graph.nodes[1]);
        assertEquals("second", graph.nodes[0].name);
    }

    public static class Graph {
        public Node[] nodes;
        public List<Node> definitions;
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    public static class Node {
        public int id;
        public String name;
    }
}
