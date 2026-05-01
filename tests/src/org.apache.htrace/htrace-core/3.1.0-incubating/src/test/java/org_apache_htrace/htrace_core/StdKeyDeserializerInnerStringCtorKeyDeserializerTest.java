/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

public class StdKeyDeserializerInnerStringCtorKeyDeserializerTest {
    @Test
    void deserializesMapKeysWithStringConstructor() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        RoutingTable table = mapper.readValue(
                """
                {
                  "routes": {
                    "trace-ingest": 7,
                    "trace-query": 11
                  }
                }
                """,
                RoutingTable.class);

        assertThat(table.routes)
                .containsEntry(new RouteKey("trace-ingest"), 7)
                .containsEntry(new RouteKey("trace-query"), 11);
    }

    public static class RoutingTable {
        public Map<RouteKey, Integer> routes;
    }

    public static final class RouteKey {
        private final String name;

        public RouteKey(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RouteKey)) {
                return false;
            }
            RouteKey routeKey = (RouteKey) other;
            return Objects.equals(name, routeKey.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }
}
