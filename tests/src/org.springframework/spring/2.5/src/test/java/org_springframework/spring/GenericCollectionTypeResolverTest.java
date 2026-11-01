/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.GenericCollectionTypeResolver;

import static org.junit.jupiter.api.Assertions.assertSame;

public class GenericCollectionTypeResolverTest {
    public static class Names extends ArrayList<String> {
    }

    public static class Counts extends HashMap<String, Integer> {
    }

    public static class Catalog {
        public List<List<String>> nested() {
            return List.of(List.of("sample"));
        }
    }

    @Test
    void resolvesCollectionMapAndNestedReturnTypes() throws Exception {
        assertSame(String.class, GenericCollectionTypeResolver.getCollectionType(Names.class));
        assertSame(String.class, GenericCollectionTypeResolver.getMapKeyType(Counts.class));
        assertSame(Integer.class, GenericCollectionTypeResolver.getMapValueType(Counts.class));
        assertSame(String.class, GenericCollectionTypeResolver.getCollectionReturnType(Catalog.class.getMethod("nested"), 2));
    }
}
