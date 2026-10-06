/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.com.google.common.collect.LinkedHashMultimap;
import org.junit.jupiter.api.Test;

public class LinkedHashMultimapTest {
    @Test
    void preservesKeyAndValueInsertionOrderDuringSerialization() throws Exception {
        LinkedHashMultimap<String, String> original = LinkedHashMultimap.create();
        original.put("operation", "query");
        original.put("operation", "mutation");
        original.put("type", "User");

        @SuppressWarnings("unchecked")
        LinkedHashMultimap<String, String> restored =
                (LinkedHashMultimap<String, String>) SerializationTest.roundTrip(original);

        assertThat(restored.entries()).containsExactlyElementsOf(original.entries());
        assertThat(restored.get("operation")).containsExactly("query", "mutation");
    }
}
