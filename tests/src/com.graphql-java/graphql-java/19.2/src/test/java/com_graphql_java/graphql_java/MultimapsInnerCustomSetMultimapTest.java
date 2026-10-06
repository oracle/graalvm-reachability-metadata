/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.com.google.common.base.Supplier;
import graphql.com.google.common.collect.Multimaps;
import graphql.com.google.common.collect.SetMultimap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomSetMultimapTest {
    @Test
    void restoresSetFactoryAndDeduplicatedValues() throws Exception {
        Map<String, Collection<String>> backingMap = new LinkedHashMap<>();
        SetMultimap<String, String> original =
                Multimaps.newSetMultimap(backingMap, StringSetSupplier.INSTANCE);
        original.put("fields", "id");
        original.put("fields", "name");
        original.put("fields", "id");

        @SuppressWarnings("unchecked")
        SetMultimap<String, String> restored =
                (SetMultimap<String, String>) SerializationTest.roundTrip(original);

        assertThat(restored.get("fields")).containsExactly("id", "name");
        restored.put("fields", "email");
        assertThat(restored.get("fields")).containsExactly("id", "name", "email");
    }

    private enum StringSetSupplier implements Supplier<Set<String>> {
        INSTANCE;

        @Override
        public Set<String> get() {
            return new LinkedHashSet<>();
        }
    }
}
