/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.com.google.common.collect.MapMaker;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ref.Reference;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;

public class MapMakerInternalMapInnerAbstractSerializationProxyTest {
    @Test
    void restoresEntriesAndMapConfigurationThroughSerializationProxy() throws Exception {
        ConcurrentMap<String, Integer> original = new MapMaker()
                .initialCapacity(4)
                .concurrencyLevel(1)
                .weakValues()
                .makeMap();
        original.put("query", 1);
        original.put("mutation", 2);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }
        try (ObjectInputStream input =
                new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            @SuppressWarnings("unchecked")
            ConcurrentMap<String, Integer> restored =
                    (ConcurrentMap<String, Integer>) input.readObject();
            try {
                // The open stream's handle table keeps the decoded weak values alive.
                System.gc();
                assertThat(restored).containsExactlyInAnyOrderEntriesOf(original);
                restored.put("subscription", 3);
                assertThat(restored).containsEntry("subscription", 3);
            } finally {
                Reference.reachabilityFence(input);
            }
        }
    }
}
