/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinFeature;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import java.util.List;
import java.util.Map;
import kotlin.Pair;
import kotlin.Triple;
import org.junit.jupiter.api.Test;

/// Explicit-null collection and map constructor parameters of Kotlin classes read as empty
/// containers through `KotlinFeature.NullToEmptyCollection` and `KotlinFeature.NullToEmptyMap`.
///
/// A Java warehouse service receives pick lists from Kotlin handhelds, which describe each
/// aisle as a Kotlin `Triple` of aisle label, bins to visit, and quantities per SKU, and tag
/// the whole list with a `Pair` of picker and priority SKUs. Older handhelds send `null` when
/// an aisle has nothing to pick, so the service enables both features and totals the picks
/// without a null check.
class KotlinNullToEmptyContainersTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new KotlinModule.Builder()
                    .enable(KotlinFeature.NullToEmptyCollection)
                    .enable(KotlinFeature.NullToEmptyMap)
                    .build());

    private static final ObjectMapper PLAIN_MAPPER = new ObjectMapper()
            .registerModule(new KotlinModule.Builder().build());

    /// The pick list a handheld uploads at the start of a shift.
    public static final class PickList {
        public Pair<String, List<String>> assignment;
        public List<Triple<String, List<String>, Map<String, Integer>>> aisles;

        int totalUnits() {
            return aisles.stream()
                    .flatMap(aisle -> aisle.getThird().values().stream())
                    .mapToInt(Integer::intValue)
                    .sum();
        }

        int binsToVisit() {
            return aisles.stream().mapToInt(aisle -> aisle.getSecond().size()).sum();
        }
    }

    private static final String UPLOAD = """
            {
              "assignment": {"first": "picker-17", "second": null},
              "aisles": [
                {"first": "A1", "second": ["A1-03", "A1-07"], "third": {"SKU-1": 4, "SKU-9": 1}},
                {"first": "B4", "second": null, "third": null},
                {"first": "C2", "second": [], "third": null},
                {"first": "D8", "second": ["D8-01"], "third": {"SKU-5": 12}}
              ]
            }
            """;

    @Test
    void readsNullContainersAsEmpty() throws Exception {
        PickList pickList = MAPPER.readValue(UPLOAD, PickList.class);

        assertEquals("picker-17", pickList.assignment.getFirst());
        assertEquals(List.of(), pickList.assignment.getSecond());
        assertEquals(4, pickList.aisles.size());

        Triple<String, List<String>, Map<String, Integer>> nulled = pickList.aisles.get(1);
        assertEquals("B4", nulled.getFirst());
        assertEquals(List.of(), nulled.getSecond());
        assertEquals(Map.of(), nulled.getThird());

        assertEquals(new Triple<>("C2", List.of(), Map.of()), pickList.aisles.get(2));

        assertEquals(17, pickList.totalUnits());
        assertEquals(3, pickList.binsToVisit());
    }

    @Test
    void keepsNullForNonContainerComponents() throws Exception {
        TypeReference<Pair<String, List<String>>> assignmentType = new TypeReference<>() {
        };

        Pair<String, List<String>> unassigned = MAPPER.readValue("""
                {"first": null, "second": null}
                """, assignmentType);

        assertNull(unassigned.getFirst());
        assertEquals(List.of(), unassigned.getSecond());
    }

    @Test
    void plainKotlinModuleKeepsNullContainers() throws Exception {
        PickList pickList = PLAIN_MAPPER.readValue(UPLOAD, PickList.class);

        assertNull(pickList.assignment.getSecond());
        assertNull(pickList.aisles.get(1).getSecond());
        assertNull(pickList.aisles.get(1).getThird());
        assertEquals(List.of(), pickList.aisles.get(2).getSecond());
        assertNull(pickList.aisles.get(2).getThird());
        assertEquals(List.of("D8-01"), pickList.aisles.get(3).getSecond());
    }

    @Test
    void forwardsNormalizedPickListWithEmptyContainers() throws Exception {
        PickList received = MAPPER.readValue(UPLOAD, PickList.class);

        JsonNode forwarded = MAPPER.readTree(MAPPER.writeValueAsString(received));

        JsonNode emptyAisle = forwarded.get("aisles").get(2);
        assertEquals("C2", emptyAisle.get("first").asText());
        assertTrue(emptyAisle.get("second").isArray());
        assertTrue(emptyAisle.get("second").isEmpty());
        assertTrue(emptyAisle.get("third").isObject());
        assertTrue(emptyAisle.get("third").isEmpty());
        assertTrue(forwarded.get("assignment").get("second").isArray());

        PickList reread = PLAIN_MAPPER.treeToValue(forwarded, PickList.class);
        assertEquals(received.aisles, reread.aisles);
        assertEquals(17, reread.totalUnits());
    }
}
