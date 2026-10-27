/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Exercises Gson's complex map-key serialization to persist shipping tariffs indexed by origin and destination.
public class ShippingTariffMapTest {

    @Test
    @Timeout(60)
    public void reloadsRouteKeysAndAppliesTariffChangesWithoutLosingDirection() {
        Gson gson = new GsonBuilder().enableComplexMapKeySerialization().create();
        Type tariffType = new TypeToken<Map<List<String>, BigDecimal>>() { }.getType();
        Map<List<String>, BigDecimal> tariffs = new LinkedHashMap<>();
        tariffs.put(List.of("north", "south"), new BigDecimal("12.50"));
        tariffs.put(List.of("south", "north"), new BigDecimal("15.00"));

        String snapshot = gson.toJson(tariffs, tariffType);
        assertEquals(new JsonParser().parse("""
                [
                  [["north", "south"], 12.50],
                  [["south", "north"], 15.00]
                ]
                """), new JsonParser().parse(snapshot));

        Map<List<String>, BigDecimal> restored = gson.fromJson(snapshot, tariffType);
        assertEquals(tariffs, restored);
        assertEquals(new BigDecimal("12.50"), restored.get(List.of("north", "south")));
        assertEquals(new BigDecimal("15.00"), restored.get(List.of("south", "north")));

        restored.put(List.of("north", "south"), new BigDecimal("13.75"));
        restored.remove(List.of("south", "north"));
        restored.put(List.of("north", "east"), new BigDecimal("9.25"));

        String updatedSnapshot = gson.toJson(restored, tariffType);
        assertEquals(new JsonParser().parse("""
                [
                  [["north", "south"], 13.75],
                  [["north", "east"], 9.25]
                ]
                """), new JsonParser().parse(updatedSnapshot));
        Map<List<String>, BigDecimal> reloaded = gson.fromJson(updatedSnapshot, tariffType);
        assertEquals(2, reloaded.size());
        assertFalse(reloaded.containsKey(List.of("south", "north")));
        BigDecimal shipmentQuote = reloaded.get(List.of("north", "south"))
                .multiply(BigDecimal.valueOf(2))
                .add(reloaded.get(List.of("north", "east")));
        assertEquals(new BigDecimal("36.75"), shipmentQuote);
    }
}
