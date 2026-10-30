/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.reflect.TypeToken;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TypesTest {
    @Test
    void resolvesRawArrayTypeAndComponentType() {
        TypeToken<List<String>[]> type = new TypeToken<List<String>[]>() { };
        assertEquals(List[].class, type.getRawType());
        assertEquals(new TypeToken<List<String>>() { }, type.getComponentType());
    }
}
