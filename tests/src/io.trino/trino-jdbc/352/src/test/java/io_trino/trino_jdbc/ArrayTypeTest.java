/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.databind.JavaType;
import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ArrayTypeTest {
    @Test
    void constructsAndChangesArrayElementType() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JavaType strings = mapper.getTypeFactory().constructArrayType(String.class);
        assertArrayEquals(new String[] {"a", "b"}, mapper.readValue("[\"a\",\"b\"]", strings));
        JavaType numbers = strings.withContentType(mapper.constructType(Integer.class));
        assertEquals(Integer[].class, numbers.getRawClass());
        assertArrayEquals(new Integer[] {2, 5}, mapper.readValue("[2,5]", numbers));
    }
}
