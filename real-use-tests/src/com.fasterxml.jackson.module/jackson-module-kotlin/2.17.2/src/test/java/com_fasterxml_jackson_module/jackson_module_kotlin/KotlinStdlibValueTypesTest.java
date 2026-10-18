/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import java.util.List;
import kotlin.Pair;
import kotlin.Triple;
import kotlin.ranges.IntRange;
import kotlin.text.Regex;
import org.junit.jupiter.api.Test;

/// Kotlin standard-library value types (Pair, Triple, IntRange, Regex) bound by the Kotlin module.
///
/// A Java dispatch service shares its routing configuration with Kotlin services, so the
/// document carries Kotlin's own value types. The service writes the configuration with an
/// `ObjectMapper` that registers `KotlinModule`, stores the JSON, and reads it back to route
/// incoming orders.
class KotlinStdlibValueTypesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new KotlinModule.Builder().build());

    /// The routing rule a dispatcher stores per depot.
    public static final class RoutingRule {
        public String depot;
        public Pair<Double, Double> location;
        public IntRange openHours;
        public Regex orderIdPattern;
        public Triple<Integer, Integer, Integer> schemaVersion;

        public RoutingRule() {
        }

        RoutingRule(String depot, Pair<Double, Double> location, IntRange openHours,
                Regex orderIdPattern, Triple<Integer, Integer, Integer> schemaVersion) {
            this.depot = depot;
            this.location = location;
            this.openHours = openHours;
            this.orderIdPattern = orderIdPattern;
            this.schemaVersion = schemaVersion;
        }

        boolean accepts(String orderId, int hour) {
            return orderIdPattern.matches(orderId) && openHours.contains(hour);
        }
    }

    @Test
    void storesAndRestoresRoutingRules() throws Exception {
        List<RoutingRule> rules = List.of(
                new RoutingRule("north", new Pair<>(59.91, 10.75), new IntRange(6, 22),
                        new Regex("N-[0-9]{4}"), new Triple<>(2, 1, 0)),
                new RoutingRule("south", new Pair<>(58.15, 7.99), new IntRange(8, 18),
                        new Regex("S-[A-Z]{2}[0-9]{2}"), new Triple<>(2, 1, 0)));

        String json = MAPPER.writeValueAsString(rules);

        JsonNode tree = MAPPER.readTree(json);
        JsonNode north = tree.get(0);
        assertEquals(59.91, north.get("location").get("first").asDouble());
        assertEquals(10.75, north.get("location").get("second").asDouble());
        assertEquals(6, north.get("openHours").get("start").asInt());
        assertEquals(22, north.get("openHours").get("end").asInt());
        assertEquals("N-[0-9]{4}", north.get("orderIdPattern").get("pattern").asText());
        assertEquals(2, north.get("schemaVersion").get("first").asInt());
        assertEquals(0, north.get("schemaVersion").get("third").asInt());

        List<RoutingRule> restored = MAPPER.readValue(json, new TypeReference<List<RoutingRule>>() {
        });

        assertEquals(2, restored.size());
        RoutingRule south = restored.get(1);
        assertEquals("south", south.depot);
        assertEquals(new Pair<>(58.15, 7.99), south.location);
        assertEquals(new IntRange(8, 18), south.openHours);
        assertEquals(new Triple<>(2, 1, 0), south.schemaVersion);
        assertEquals("S-[A-Z]{2}[0-9]{2}", south.orderIdPattern.getPattern());

        assertTrue(south.accepts("S-AB12", 9));
        assertFalse(south.accepts("S-AB12", 19));
        assertFalse(south.accepts("N-1234", 9));
        assertTrue(restored.get(0).accepts("N-1234", 21));
    }

    @Test
    void readsHandWrittenConfiguration() throws Exception {
        String json = """
                {
                  "depot": "west",
                  "location": {"first": 60.39, "second": 5.32},
                  "openHours": {"start": 7, "end": 15},
                  "orderIdPattern": {"pattern": "w-\\\\d+", "options": ["IGNORE_CASE"]},
                  "schemaVersion": {"first": 3, "second": 0, "third": 4}
                }
                """;

        RoutingRule west = MAPPER.readValue(json, RoutingRule.class);

        assertEquals(new Pair<>(60.39, 5.32), west.location);
        assertEquals(7, west.openHours.getFirst());
        assertEquals(15, west.openHours.getLast());
        assertEquals(new Triple<>(3, 0, 4), west.schemaVersion);
        assertTrue(west.accepts("W-42", 7));
        assertTrue(west.accepts("w-7", 15));
        assertFalse(west.accepts("W-x", 10));
        assertFalse(west.accepts("W-42", 16));
    }
}
