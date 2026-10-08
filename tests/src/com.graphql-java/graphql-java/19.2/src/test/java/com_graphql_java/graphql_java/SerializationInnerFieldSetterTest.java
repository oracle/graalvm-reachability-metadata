/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.com.google.common.collect.ImmutableListMultimap;
import org.junit.jupiter.api.Test;

public class SerializationInnerFieldSetterTest {
    @Test
    void restoresImmutableMultimapMapAndSizeFields() throws Exception {
        ImmutableListMultimap<String, String> original =
                ImmutableListMultimap.<String, String>builder()
                        .put("fields", "id")
                        .put("fields", "name")
                        .put("types", "User")
                        .build();

        @SuppressWarnings("unchecked")
        ImmutableListMultimap<String, String> restored =
                (ImmutableListMultimap<String, String>) SerializationTest.roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.size()).isEqualTo(3);
        assertThat(restored.get("fields")).containsExactly("id", "name");
    }
}
