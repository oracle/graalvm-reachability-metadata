/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.com.google.common.collect.ImmutableSetMultimap;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

public class ImmutableSetMultimapTest {
    @Test
    void restoresEntriesAndValueComparator() throws Exception {
        ImmutableSetMultimap<String, String> original =
                ImmutableSetMultimap.<String, String>builder()
                        .orderValuesBy(Comparator.reverseOrder())
                        .put("fields", "id")
                        .put("fields", "name")
                        .put("directives", "skip")
                        .build();

        @SuppressWarnings("unchecked")
        ImmutableSetMultimap<String, String> restored =
                (ImmutableSetMultimap<String, String>) SerializationTest.roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.get("fields")).containsExactly("name", "id");
    }
}
