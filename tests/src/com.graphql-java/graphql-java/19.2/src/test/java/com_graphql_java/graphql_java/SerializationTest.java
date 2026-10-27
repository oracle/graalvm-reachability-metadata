/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.com.google.common.collect.ImmutableSetMultimap;
import graphql.com.google.common.collect.LinkedHashMultiset;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class SerializationTest {
    @Test
    void roundTripsMultisetsAndMultimaps() throws Exception {
        LinkedHashMultiset<String> multiset = LinkedHashMultiset.create();
        multiset.add("query", 2);
        multiset.add("mutation", 1);
        assertThat(roundTrip(multiset)).isEqualTo(multiset);

        ImmutableSetMultimap<String, String> multimap =
                ImmutableSetMultimap.<String, String>builder()
                        .put("fields", "id")
                        .put("fields", "name")
                        .build();
        assertThat(roundTrip(multimap)).isEqualTo(multimap);
    }

    static Object roundTrip(Object value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input =
                new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return input.readObject();
        }
    }
}
