/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import java.util.List;
import java.util.Optional;
import kotlin.ranges.CharRange;
import kotlin.ranges.ClosedRange;
import kotlin.ranges.IntRange;
import kotlin.ranges.LongRange;
import org.junit.jupiter.api.Test;

/// Polymorphic Kotlin ranges (`IntRange`, `LongRange`, `CharRange`) resolved from `@JsonTypeInfo`
/// type ids and bound through the Kotlin module's `ClosedRangeMixin` creator.
///
/// A Java sharding service keeps a shard map that Kotlin tenants edit. Each shard owns one key
/// range, and the partitioning scheme decides its kind: tenant ids form an `IntRange`, event
/// sequence numbers a `LongRange`, and customer surnames a `CharRange` of initial letters. The
/// map stores every range behind a `scheme` type id, and the router sends each key to the shard
/// whose range contains it, so the ranges must come back as the concrete Kotlin types.
class KotlinPolymorphicRangesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new KotlinModule.Builder().build());

    /// The shard map as the service stores it.
    public static final class ShardMap {
        public int revision;
        public List<ShardAssignment> assignments;
    }

    /// One shard and the range of keys it owns.
    public static final class ShardAssignment {
        public String shard;

        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "scheme")
        @JsonSubTypes({
                @JsonSubTypes.Type(value = IntRange.class, name = "tenant"),
                @JsonSubTypes.Type(value = LongRange.class, name = "sequence"),
                @JsonSubTypes.Type(value = CharRange.class, name = "alphabet")
        })
        public ClosedRange<?> keys;

        static ShardAssignment of(String shard, ClosedRange<?> keys) {
            ShardAssignment assignment = new ShardAssignment();
            assignment.shard = shard;
            assignment.keys = keys;
            return assignment;
        }
    }

    private static boolean owns(ShardAssignment assignment, Object key) {
        if (assignment.keys instanceof IntRange tenants && key instanceof Integer tenant) {
            return tenants.contains(tenant.intValue());
        }
        if (assignment.keys instanceof LongRange sequences && key instanceof Long sequence) {
            return sequences.contains(sequence.longValue());
        }
        if (assignment.keys instanceof CharRange letters && key instanceof Character letter) {
            return letters.contains(letter.charValue());
        }
        return false;
    }

    private static Optional<String> route(ShardMap map, Object key) {
        return map.assignments.stream()
                .filter(assignment -> owns(assignment, key))
                .map(assignment -> assignment.shard)
                .findFirst();
    }

    @Test
    void readsEachSchemeAsItsKotlinRangeAndRoutesKeys() throws Exception {
        String stored = """
                {
                  "revision": 7,
                  "assignments": [
                    {"shard": "tenants-a", "keys": {"scheme": "tenant", "start": 1, "end": 4999}},
                    {"shard": "tenants-b", "keys": {"start": 5000, "end": 9999, "scheme": "tenant"}},
                    {"shard": "events", "keys": {"scheme": "sequence", "start": 4000000000, "end": 9000000000}},
                    {"shard": "surnames-am", "keys": {"start": "A", "scheme": "alphabet", "end": "M"}},
                    {"shard": "surnames-nz", "keys": {"scheme": "alphabet", "start": "N", "end": "Z"}}
                  ]
                }
                """;

        ShardMap map = MAPPER.readValue(stored, ShardMap.class);

        assertEquals(7, map.revision);
        assertEquals(5, map.assignments.size());
        assertEquals(new IntRange(1, 4999), assertInstanceOf(IntRange.class, map.assignments.get(0).keys));
        assertEquals(new IntRange(5000, 9999), assertInstanceOf(IntRange.class, map.assignments.get(1).keys));
        LongRange events = assertInstanceOf(LongRange.class, map.assignments.get(2).keys);
        assertEquals(new LongRange(4_000_000_000L, 9_000_000_000L), events);
        assertEquals(9_000_000_000L, events.getLast());
        assertEquals(new CharRange('A', 'M'), assertInstanceOf(CharRange.class, map.assignments.get(3).keys));
        assertEquals(new CharRange('N', 'Z'), assertInstanceOf(CharRange.class, map.assignments.get(4).keys));

        assertEquals(Optional.of("tenants-a"), route(map, 4999));
        assertEquals(Optional.of("tenants-b"), route(map, 5000));
        assertEquals(Optional.of("events"), route(map, 8_123_456_789L));
        assertEquals(Optional.of("surnames-am"), route(map, 'K'));
        assertEquals(Optional.of("surnames-nz"), route(map, 'N'));
        assertEquals(Optional.empty(), route(map, 10_000));
        assertEquals(Optional.empty(), route(map, 'k'));
    }

    @Test
    void writesTheSchemeWithTheRangeBoundsAndReadsItBack() throws Exception {
        ShardMap map = new ShardMap();
        map.revision = 8;
        map.assignments = List.of(
                ShardAssignment.of("tenants", new IntRange(1, 9999)),
                ShardAssignment.of("events", new LongRange(0L, 12_000_000_000L)),
                ShardAssignment.of("surnames", new CharRange('A', 'Z')));

        String stored = MAPPER.writeValueAsString(map);

        JsonNode assignments = MAPPER.readTree(stored).get("assignments");
        JsonNode tenants = assignments.get(0).get("keys");
        assertEquals("tenant", tenants.get("scheme").asText());
        assertEquals(1, tenants.get("start").asInt());
        assertEquals(9999, tenants.get("end").asInt());
        assertFalse(tenants.has("first"));
        assertFalse(tenants.has("step"));
        JsonNode events = assignments.get(1).get("keys");
        assertEquals("sequence", events.get("scheme").asText());
        assertEquals(12_000_000_000L, events.get("end").asLong());
        JsonNode surnames = assignments.get(2).get("keys");
        assertEquals("alphabet", surnames.get("scheme").asText());
        assertEquals("A", surnames.get("start").asText());
        assertEquals("Z", surnames.get("end").asText());

        ShardMap reloaded = MAPPER.readValue(stored, ShardMap.class);
        assertEquals(8, reloaded.revision);
        assertEquals(map.assignments.get(0).keys, reloaded.assignments.get(0).keys);
        assertEquals(map.assignments.get(1).keys, reloaded.assignments.get(1).keys);
        assertEquals(map.assignments.get(2).keys, reloaded.assignments.get(2).keys);
        assertEquals(Optional.of("events"), route(reloaded, 11_999_999_999L));
        assertTrue(owns(reloaded.assignments.get(2), 'Q'));
    }

    @Test
    void rejectsAShardWhoseSchemeIsUnknown() {
        String stored = """
                {"revision": 9, "assignments": [
                  {"shard": "regions", "keys": {"scheme": "geohash", "start": "u0", "end": "u9"}}
                ]}
                """;

        InvalidTypeIdException failure = assertThrows(InvalidTypeIdException.class,
                () -> MAPPER.readValue(stored, ShardMap.class));

        assertEquals("geohash", failure.getTypeId());
        assertEquals(ClosedRange.class, failure.getBaseType().getRawClass());
    }
}
