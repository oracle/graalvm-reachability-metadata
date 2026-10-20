/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;

/**
 * Map serialization with non-string keys: `enableComplexMapKeySerialization`, enum and numeric
 * keys, and the concrete map types Gson creates for declared map interfaces.
 *
 * A freight broker caches each carrier's rate card as JSON. Rates are keyed by a `Lane` record,
 * pickup windows by a weekday enum, and surcharges by weight bracket. The tests write and read
 * such cards and assert on the JSON a partner system receives and on the restored maps.
 */
public class FreightRateCardMapKeysTest {

    private static final Gson CACHE_GSON = new GsonBuilder()
            .enableComplexMapKeySerialization()
            .create();

    private static final Gson PLAIN_GSON = new Gson();

    @Test
    void writesLaneKeyedRatesAsKeyValuePairsAndRestoresThem() {
        RateCard card = sampleCard();

        String json = CACHE_GSON.toJson(card);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("NordFreight", root.get("carrier").getAsString());
        JsonArray laneRates = root.getAsJsonArray("laneRates");
        assertEquals(2, laneRates.size(), json);
        JsonArray firstPair = laneRates.get(0).getAsJsonArray();
        JsonObject firstLane = firstPair.get(0).getAsJsonObject();
        assertEquals("RTM", firstLane.get("origin").getAsString());
        assertEquals("HAM", firstLane.get("destination").getAsString());
        assertEquals(new BigDecimal("120.50"), firstPair.get(1).getAsBigDecimal());

        JsonObject windows = root.getAsJsonObject("pickupWindows");
        assertEquals(List.of("mon", "fri"), List.copyOf(windows.keySet()),
                "enum keys use their @SerializedName");
        assertEquals("08:00-12:00", windows.getAsJsonArray("mon").get(0).getAsString());

        JsonObject brackets = root.getAsJsonObject("weightSurcharges");
        assertEquals(List.of("5", "30", "1000"), List.copyOf(brackets.keySet()),
                "primitive keys stay object member names even with complex keys enabled");

        RateCard restored = CACHE_GSON.fromJson(json, RateCard.class);

        assertEquals(card.carrier, restored.carrier);
        assertEquals(card.laneRates, restored.laneRates);
        assertEquals(new BigDecimal("98.00"), restored.laneRates.get(new Lane("ANR", "PRG")));
        assertInstanceOf(EnumMap.class, restored.pickupWindows);
        assertEquals(card.pickupWindows, restored.pickupWindows);
        assertInstanceOf(TreeMap.class, restored.weightSurcharges);
        assertEquals(List.of(5, 30, 1000), List.copyOf(restored.weightSurcharges.keySet()));
        assertEquals("pallet", restored.weightSurcharges.get(30));
    }

    @Test
    void readsPartnerCardsWithNumericAndEnumKeysIntoTheDeclaredMapTypes() {
        String partnerCard = """
                {
                  "carrier": "AlpTrans",
                  "pickupWindows": {"fri": ["13:00-17:00"], "mon": ["07:00-09:00", "15:00-18:00"]},
                  "weightSurcharges": {"1000": "freight", "5": "parcel", "250": "pallet"},
                  "fuelIndexByLane": {"7": 1.12, "3": 0.97}
                }
                """;

        RateCard card = PLAIN_GSON.fromJson(partnerCard, RateCard.class);

        assertEquals("AlpTrans", card.carrier);
        assertEquals(List.of(Weekday.MONDAY, Weekday.FRIDAY), List.copyOf(card.pickupWindows.keySet()),
                "an EnumMap orders its keys by ordinal, not by input order");
        assertEquals(List.of("07:00-09:00", "15:00-18:00"), card.pickupWindows.get(Weekday.MONDAY));
        assertEquals(List.of(5, 250, 1000), List.copyOf(card.weightSurcharges.keySet()),
                "a SortedMap<Integer, ?> sorts numerically");
        assertInstanceOf(ConcurrentHashMap.class, card.fuelIndexByLane);
        assertEquals(Map.of(3L, 0.97, 7L, 1.12), card.fuelIndexByLane);
    }

    @Test
    void readsATopLevelLaneMapFromEitherPairArraysOrPlainObjects() {
        Type laneRatesType = new TypeToken<Map<Lane, BigDecimal>>() { }.getType();
        String pairs = """
                [[{"origin": "GDN", "destination": "BER"}, 210.00],
                 [{"origin": "BER", "destination": "GDN"}, 185.25]]
                """;

        Map<Lane, BigDecimal> fromPairs = CACHE_GSON.fromJson(pairs, laneRatesType);

        assertEquals(2, fromPairs.size());
        assertEquals(new BigDecimal("210.00"), fromPairs.get(new Lane("GDN", "BER")));
        assertEquals(new BigDecimal("185.25"), fromPairs.get(new Lane("BER", "GDN")));

        Type statusType = new TypeToken<Map<Weekday, Boolean>>() { }.getType();
        Map<Weekday, Boolean> depotOpen = CACHE_GSON.fromJson("{\"mon\": true, \"fri\": false}", statusType);
        assertEquals(Boolean.TRUE, depotOpen.get(Weekday.MONDAY));
        assertEquals(Boolean.FALSE, depotOpen.get(Weekday.FRIDAY));
    }

    @Test
    void plainGsonFlattensCompositeKeysToStringsThatCannotBeReadBack() {
        Map<Lane, BigDecimal> rates = new LinkedHashMap<>();
        rates.put(new Lane("RTM", "HAM"), new BigDecimal("120.50"));

        JsonElement tree = PLAIN_GSON.toJsonTree(rates);

        assertTrue(tree.isJsonObject(), tree.toString());
        JsonObject object = tree.getAsJsonObject();
        assertEquals(List.of("Lane[origin=RTM, destination=HAM]"), List.copyOf(object.keySet()),
                "without complex key serialization the key is written with toString()");

        Type laneRatesType = new TypeToken<Map<Lane, BigDecimal>>() { }.getType();
        assertThrows(JsonSyntaxException.class, () -> PLAIN_GSON.fromJson(tree, laneRatesType));
    }

    @Test
    void rejectsDuplicateKeysAndDropsNullValuedEntries() {
        Type bracketType = new TypeToken<SortedMap<Integer, String>>() { }.getType();

        JsonSyntaxException duplicate = assertThrows(JsonSyntaxException.class,
                () -> PLAIN_GSON.fromJson("{\"30\": \"pallet\", \"30\": \"freight\"}", bracketType));
        assertTrue(duplicate.getMessage().contains("duplicate key: 30"), duplicate.getMessage());

        Type laneRatesType = new TypeToken<Map<Lane, BigDecimal>>() { }.getType();
        assertThrows(JsonSyntaxException.class, () -> CACHE_GSON.fromJson("""
                [[{"origin": "RTM", "destination": "HAM"}, 1.00],
                 [{"origin": "RTM", "destination": "HAM"}, 2.00]]
                """, laneRatesType));

        Map<Integer, String> withGap = new TreeMap<>();
        withGap.put(5, "parcel");
        withGap.put(30, null);
        JsonObject written = PLAIN_GSON.toJsonTree(withGap).getAsJsonObject();
        assertTrue(written.has("5"));
        assertFalse(written.has("30"), "null map values are dropped unless serializeNulls is set");
        JsonObject withNulls = new GsonBuilder().serializeNulls().create()
                .toJsonTree(withGap).getAsJsonObject();
        assertTrue(withNulls.get("30").isJsonNull());
    }

    private static RateCard sampleCard() {
        RateCard card = new RateCard();
        card.carrier = "NordFreight";
        card.laneRates = new LinkedHashMap<>();
        card.laneRates.put(new Lane("RTM", "HAM"), new BigDecimal("120.50"));
        card.laneRates.put(new Lane("ANR", "PRG"), new BigDecimal("98.00"));
        card.pickupWindows = new EnumMap<>(Weekday.class);
        card.pickupWindows.put(Weekday.MONDAY, List.of("08:00-12:00"));
        card.pickupWindows.put(Weekday.FRIDAY, List.of("10:00-14:00", "16:00-18:00"));
        card.weightSurcharges = new TreeMap<>();
        card.weightSurcharges.put(1000, "freight");
        card.weightSurcharges.put(5, "parcel");
        card.weightSurcharges.put(30, "pallet");
        return card;
    }

    record Lane(String origin, String destination) {
    }

    enum Weekday {
        @SerializedName("mon")
        MONDAY,
        @SerializedName("fri")
        FRIDAY
    }

    static final class RateCard {
        String carrier;
        Map<Lane, BigDecimal> laneRates;
        EnumMap<Weekday, List<String>> pickupWindows;
        SortedMap<Integer, String> weightSurcharges;
        ConcurrentMap<Long, Double> fuelIndexByLane;
    }
}
