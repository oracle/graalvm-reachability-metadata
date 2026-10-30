/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_jcdi_1_0_spec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.enterprise.util.TypeLiteral;
import org.junit.jupiter.api.Test;

public class TypeLiteralTest {
    @Test
    void capturesSimpleAndArrayTypes() {
        TypeLiteral<String> text = new TypeLiteral<String>() { };
        TypeLiteral<String[]> array = new TypeLiteral<String[]>() { };

        assertSame(String.class, text.getType());
        assertSame(String.class, text.getRawType());
        assertSame(String[].class, array.getType());
        assertSame(String[].class, array.getRawType());
    }

    @Test
    void retainsNestedGenericArguments() {
        TypeLiteral<Map<String, List<Integer>>> literal = new TypeLiteral<Map<String, List<Integer>>>() { };
        ParameterizedType mapType = assertInstanceOf(ParameterizedType.class, literal.getType());

        assertSame(Map.class, literal.getRawType());
        assertSame(Map.class, mapType.getRawType());
        assertEquals(2, mapType.getActualTypeArguments().length);
        assertSame(String.class, mapType.getActualTypeArguments()[0]);
        ParameterizedType valuesType = assertInstanceOf(ParameterizedType.class, mapType.getActualTypeArguments()[1]);
        assertSame(List.class, valuesType.getRawType());
        assertArrayEquals(new Type[] {Integer.class}, valuesType.getActualTypeArguments());
    }

    @Test
    void retainsWildcardBounds() {
        TypeLiteral<List<? extends Number>> literal = new TypeLiteral<List<? extends Number>>() { };
        ParameterizedType listType = assertInstanceOf(ParameterizedType.class, literal.getType());
        WildcardType elementType = assertInstanceOf(WildcardType.class, listType.getActualTypeArguments()[0]);

        assertSame(List.class, literal.getRawType());
        assertArrayEquals(new Type[] {Number.class}, elementType.getUpperBounds());
        assertEquals(0, elementType.getLowerBounds().length);
    }

    @Test
    void retainsGenericArrayComponentType() {
        TypeLiteral<List<String>[]> literal = new TypeLiteral<List<String>[]>() { };
        GenericArrayType arrayType = assertInstanceOf(GenericArrayType.class, literal.getType());
        ParameterizedType component = assertInstanceOf(ParameterizedType.class, arrayType.getGenericComponentType());

        assertSame(List.class, component.getRawType());
        assertArrayEquals(new Type[] {String.class}, component.getActualTypeArguments());
    }

    @Test
    void resolvesTypeThroughNonParameterizedSubclass() {
        TypeLiteral<List<String>> direct = new StringListLiteral();
        TypeLiteral<List<String>> inherited = new InheritedStringListLiteral();

        assertEquals(direct.getType(), inherited.getType());
        assertSame(List.class, inherited.getRawType());
    }

    @Test
    void supportsNamedLiteralsAsLookupKeys() {
        StringListLiteral first = new StringListLiteral();
        StringListLiteral same = new StringListLiteral();
        Map<TypeLiteral<?>, String> names = new HashMap<>();
        names.put(first, "text-list");

        assertEquals(first, first);
        assertEquals(first, same);
        assertEquals(same, first);
        assertEquals(first.hashCode(), same.hashCode());
        assertEquals("text-list", names.get(same));
        assertNotEquals(first, null);
        assertNotEquals(first, "text-list");
        assertNotEquals(first, new TypeLiteral<List<Integer>>() { });
    }

    private static class StringListLiteral extends TypeLiteral<List<String>> {
    }

    private static final class InheritedStringListLiteral extends StringListLiteral {
    }
}
