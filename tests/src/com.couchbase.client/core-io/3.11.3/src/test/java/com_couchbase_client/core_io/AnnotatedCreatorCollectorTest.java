/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonCreator;
import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonProperty;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotatedCreatorCollectorTest {
    @Test
    void mapsApplicationData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(Coordinate.class, CoordinateMixin.class);
        Coordinate result = mapper.readValue("""
                {"x":4,"y":9}
                """, Coordinate.class);
        assertEquals(4, result.x);
        assertEquals(9, result.y);
    }

    public static class Coordinate {
        public final int x;
        public final int y;

        private Coordinate(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public static Coordinate of(int x, int y) {
            return new Coordinate(x, y);
        }
    }

    public abstract static class CoordinateMixin {
        @JsonCreator
        public static Coordinate of(@JsonProperty("x") int x, @JsonProperty("y") int y) {
            return Coordinate.of(x, y);
        }
    }
}
