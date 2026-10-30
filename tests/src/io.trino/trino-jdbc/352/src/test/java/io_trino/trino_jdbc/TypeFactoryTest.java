/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.databind.JavaType;
import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TypeFactoryTest {
    @Test
    void resolvesCanonicalGenericTypeForReading() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JavaType type = mapper.getTypeFactory().constructFromCanonical("java.util.Map<java.lang.String,java.lang.Integer>");
        Map<String, Integer> data = mapper.readValue("{\"count\":14}", type);
        assertEquals(Map.of("count", 14), data);
    }
}
