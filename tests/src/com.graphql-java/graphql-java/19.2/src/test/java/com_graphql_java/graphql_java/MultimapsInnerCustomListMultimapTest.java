/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.com.google.common.base.Supplier;
import graphql.com.google.common.collect.ListMultimap;
import graphql.com.google.common.collect.Multimaps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomListMultimapTest {
    @Test
    void restoresCustomListFactoryAndValues() throws Exception {
        Map<String, Collection<String>> backingMap = new LinkedHashMap<>();
        ListMultimap<String, String> original =
                Multimaps.newListMultimap(backingMap, StringListSupplier.INSTANCE);
        original.put("fields", "id");
        original.put("fields", "name");

        @SuppressWarnings("unchecked")
        ListMultimap<String, String> restored =
                (ListMultimap<String, String>) SerializationTest.roundTrip(original);

        assertThat(restored.get("fields")).containsExactly("id", "name");
        restored.put("fields", "email");
        assertThat(restored.get("fields")).containsExactly("id", "name", "email");
    }

    private enum StringListSupplier implements Supplier<List<String>> {
        INSTANCE;

        @Override
        public List<String> get() {
            return new ArrayList<>();
        }
    }
}
