/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.collect.ImmutableListMultimap;
import io.trino.jdbc.$internal.guava.collect.SetMultimap;
import io.trino.jdbc.$internal.jackson.core.type.TypeReference;
import io.trino.jdbc.$internal.jackson.databind.DeserializationFeature;
import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import io.trino.jdbc.$internal.jackson.datatype.guava.GuavaModule;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GuavaMultimapDeserializerTest {
    @Test
    void readsSetMultimapInterfaceWithoutConcreteFactory() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new GuavaModule());
        TypeReference<SetMultimap<String, Integer>> type = new TypeReference<SetMultimap<String, Integer>>() { };
        SetMultimap<String, Integer> values = mapper.readValue("{\"a\":[2,2,3],\"b\":[5]}", type);
        assertEquals(Set.of(2, 3), values.get("a"));
        assertEquals(Set.of(5), values.get("b"));
        assertEquals(3, values.size());
    }

    @Test
    void convertsJsonArraysAndSingleValuesToImmutableMultimap() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new GuavaModule());
        TypeReference<ImmutableListMultimap<String, Integer>> type = new TypeReference<ImmutableListMultimap<String, Integer>>() { };
        ImmutableListMultimap<String, Integer> values = mapper.readValue("{\"a\":[2,3]}", type);
        assertEquals(List.of(2, 3), values.get("a"));
        mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        ImmutableListMultimap<String, Integer> single = mapper.readValue("{\"b\":5}", type);
        assertEquals(List.of(5), single.get("b"));
    }
}
