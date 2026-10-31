/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.JavaType;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class ArrayTypeTest {
    @Test
    void mapsApplicationData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JavaType strings = mapper.getTypeFactory().constructArrayType(String.class);
        JavaType integers = strings.withContentType(mapper.constructType(Integer.class));
        assertArrayEquals(new String[]{"a", "b"}, (String[]) mapper.readValue("""
                ["a","b"]
                """, strings));
        assertArrayEquals(new Integer[]{3, 7}, (Integer[]) mapper.readValue("""
                [3,7]
                """, integers));
    }
}
