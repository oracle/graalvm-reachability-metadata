/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.reflect.TypeParameter;
import io.trino.jdbc.$internal.guava.reflect.TypeToken;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TypesInnerJavaVersionAnonymous3Test {
    @Test
    void rendersResolvedGenericTypeName() {
        TypeToken<?> strings = listOf(String.class);
        assertEquals("java.util.List<java.lang.String>", strings.toString());
        assertEquals(new TypeToken<List<String>>() { }, strings);
    }

    private static <T> TypeToken<List<T>> listOf(Class<T> component) {
        return new TypeToken<List<T>>() { }.where(new TypeParameter<T>() { }, component);
    }
}
