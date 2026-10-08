/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.com.google.common.base.Supplier;
import graphql.com.google.common.collect.Multimap;
import graphql.com.google.common.collect.Multimaps;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MultimapsInnerCustomMultimapTest {
    @Test
    void restoresGeneralCollectionFactoryAndValues() throws Exception {
        Map<String, Collection<String>> backingMap = new LinkedHashMap<>();
        Multimap<String, String> original =
                Multimaps.newMultimap(backingMap, StringDequeSupplier.INSTANCE);
        original.put("operations", "query");
        original.put("operations", "mutation");

        @SuppressWarnings("unchecked")
        Multimap<String, String> restored =
                (Multimap<String, String>) SerializationTest.roundTrip(original);

        assertThat(restored.get("operations")).containsExactly("query", "mutation");
        restored.put("operations", "subscription");
        assertThat(restored.get("operations"))
                .containsExactly("query", "mutation", "subscription");
    }

    private enum StringDequeSupplier implements Supplier<Collection<String>> {
        INSTANCE;

        @Override
        public Collection<String> get() {
            return new ArrayDeque<>();
        }
    }
}
