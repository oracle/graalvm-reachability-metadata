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
import graphql.com.google.common.collect.SortedSetMultimap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomSortedSetMultimapTest {
    @Test
    void restoresSortedSetFactoryAndOrdering() throws Exception {
        Map<String, Collection<String>> backingMap = new LinkedHashMap<>();
        SortedSetMultimap<String, String> original =
                Multimaps.newSortedSetMultimap(backingMap, StringSortedSetSupplier.INSTANCE);
        original.put("fields", "name");
        original.put("fields", "id");

        @SuppressWarnings("unchecked")
        SortedSetMultimap<String, String> restored =
                (SortedSetMultimap<String, String>) SerializationTest.roundTrip(original);

        assertThat(restored.get("fields")).containsExactly("id", "name");
        restored.put("fields", "email");
        assertThat(restored.get("fields")).containsExactly("email", "id", "name");
        assertThat(restored.valueComparator()).isNull();
    }

    private enum StringSortedSetSupplier implements Supplier<SortedSet<String>> {
        INSTANCE;

        @Override
        public SortedSet<String> get() {
            return new TreeSet<>();
        }
    }
}
