/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_inject.guice;

import com.google.inject.TypeLiteral;
import com.google.inject.util.Types;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MoreTypesTest {
    @Test
    void resolvesRawArrayTypesForParameterizedComponents() {
        Type array = Types.arrayOf(Types.listOf(String.class));
        TypeLiteral<?> literal = TypeLiteral.get(array);
        assertEquals(List[].class, literal.getRawType());
        assertEquals(new TypeLiteral<List<String>[]>() { }, literal);

        TypeLiteral<?> matrix = TypeLiteral.get(Types.arrayOf(array));
        assertEquals(List[][].class, matrix.getRawType());
        assertEquals(new TypeLiteral<List<String>[][]>() { }, matrix);
    }
}
