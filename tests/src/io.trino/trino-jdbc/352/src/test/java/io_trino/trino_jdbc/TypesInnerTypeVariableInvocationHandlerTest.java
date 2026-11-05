/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.reflect.TypeToken;
import java.lang.reflect.TypeVariable;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TypesInnerTypeVariableInvocationHandlerTest {
    @Test
    void exposesCapturedWildcardBounds() {
        TypeToken<List<? extends Number>> numbers = new TypeToken<List<? extends Number>>() { };
        TypeVariable<?> captured = (TypeVariable<?>) numbers.resolveType(List.class.getTypeParameters()[0]).getType();
        assertArrayEquals(new Object[] {Number.class}, captured.getBounds());
        assertTrue(captured.getName().contains("capture"));
    }
}
