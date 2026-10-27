/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.com.google.common.collect.MapMaker;
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

        @SuppressWarnings("unchecked")
        ConcurrentMap<String, Integer> restored =
                (ConcurrentMap<String, Integer>) SerializationTest.roundTrip(original);

        assertThat(restored).containsExactlyInAnyOrderEntriesOf(original);
        restored.put("subscription", 3);
        assertThat(restored).containsEntry("subscription", 3);
    }
}
