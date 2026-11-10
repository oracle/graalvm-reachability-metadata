/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.core.type.TypeReference;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.JavaType;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.type.TypeFactory;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TypeFactoryTest {
    @Test
    void mapsApplicationData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JavaType type = mapper.getTypeFactory().constructFromCanonical("java.util.List<java.lang.String>");
        assertEquals(List.of("a", "b"), mapper.readValue("""
                ["a","b"]
                """, type));
        assertEquals(String.class, type.getContentType().getRawClass());
    }

    @Test
    void resolvesGenericArrayErasureAndDeserializesItsElements() throws Exception {
        TypeReference<List<String>[]> reference = new TypeReference<List<String>[]>() { };
        assertEquals(List[].class, TypeFactory.rawClass(reference.getType()));
        List<String>[] values = new ObjectMapper().readValue("""
                [["north","south"],["east"]]
                """, reference);
        assertEquals(2, values.length);
        assertEquals(List.of("north", "south"), values[0]);
        assertEquals(List.of("east"), values[1]);
    }

    @Test
    void resolvesClassesWithoutAContextClassLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(null);
            TypeFactory factory = new ObjectMapper().getTypeFactory().withClassLoader(null);
            assertEquals(String.class, factory.findClass("java.lang.String"));
            assertEquals(int.class, factory.findClass("int"));
        } finally {
            thread.setContextClassLoader(original);
        }
    }
}
