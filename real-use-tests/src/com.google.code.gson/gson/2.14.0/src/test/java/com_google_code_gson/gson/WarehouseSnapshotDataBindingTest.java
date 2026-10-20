/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reflective data binding of application classes, records, enums, and generic collections.
 *
 * A warehouse service persists its inventory snapshot to a JSON file with `Gson.toJson` and
 * restores it with `Gson.fromJson`: mutable classes with inherited and transient fields,
 * immutable records, enum constants, nested lists and maps, and a top-level generic list read
 * through a `TypeToken`. Each test asserts on the file content and on the restored objects.
 */
public class WarehouseSnapshotDataBindingTest {

    @TempDir
    Path workDir;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Test
    void persistsAndRestoresASnapshotFile() throws IOException {
        Path file = workDir.resolve("snapshot.json");
        Snapshot snapshot = sampleSnapshot();

        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(snapshot, writer);
        }

        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(written.contains("\n  \"warehouse\": \"North Depot\""), written);
        JsonObject json = JsonParser.parseString(written).getAsJsonObject();
        assertEquals("North Depot", json.get("warehouse").getAsString());
        assertEquals(3, json.get("revision").getAsInt());
        assertFalse(json.has("cacheKey"), "transient fields must not be written");
        assertFalse(json.has("auditor"), "null fields are omitted by default");

        JsonArray items = json.getAsJsonArray("items");
        assertEquals(2, items.size());
        JsonObject bolt = items.get(0).getAsJsonObject();
        assertEquals("BOLT-M8", bolt.get("sku").getAsString());
        assertEquals("FASTENERS", bolt.get("category").getAsString());
        assertEquals("A-01-3", bolt.getAsJsonObject("location").get("bin").getAsString());
        assertEquals(1200, bolt.getAsJsonObject("stockByZone").get("main").getAsInt());
        assertEquals("tracked", items.get(1).getAsJsonObject().get("serial_policy").getAsString());

        Snapshot restored;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            restored = gson.fromJson(reader, Snapshot.class);
        }

        assertNotNull(restored);
        assertEquals("North Depot", restored.warehouse);
        assertEquals(3, restored.revision);
        assertEquals("snap-0003", restored.snapshotId, "the inherited field must be restored");
        assertNull(restored.cacheKey);
        assertNull(restored.auditor);
        assertEquals(2, restored.items.size());

        StockItem restoredBolt = restored.items.get(0);
        assertEquals("BOLT-M8", restoredBolt.sku);
        assertEquals(Category.FASTENERS, restoredBolt.category);
        assertEquals(new BinLocation("A", 1, "A-01-3"), restoredBolt.location);
        assertEquals(List.of("main", "overflow"), List.copyOf(restoredBolt.stockByZone.keySet()));
        assertEquals(Integer.valueOf(300), restoredBolt.stockByZone.get("overflow"));
        assertEquals(List.of("steel", "zinc-plated"), restoredBolt.tags);

        StockItem drill = restored.items.get(1);
        assertEquals(Category.POWER_TOOLS, drill.category);
        assertEquals(SerialPolicy.TRACKED, drill.serialPolicy);
        assertEquals(149.90, drill.unitPrice, 0.0001);
    }

    @Test
    void readsAGenericListOfRecordsThroughATypeToken() {
        String movements = """
                [
                  {"sku": "BOLT-M8", "quantity": -40, "from": {"aisle": "A", "shelf": 1, "bin": "A-01-3"}},
                  {"sku": "DRILL-18V", "quantity": 5, "to": {"aisle": "C", "shelf": 4, "bin": "C-04-1"}}
                ]
                """;

        List<Movement> parsed = gson.fromJson(movements, new TypeToken<List<Movement>>() { }.getType());

        assertEquals(2, parsed.size());
        assertEquals(new Movement("BOLT-M8", -40, new BinLocation("A", 1, "A-01-3"), null), parsed.get(0));
        Movement inbound = parsed.get(1);
        assertEquals("DRILL-18V", inbound.sku());
        assertEquals(5, inbound.quantity());
        assertNull(inbound.from());
        assertEquals("C-04-1", inbound.to().bin());

        String rewritten = gson.toJson(parsed, new TypeToken<List<Movement>>() { }.getType());
        JsonArray array = JsonParser.parseString(rewritten).getAsJsonArray();
        assertEquals(-40, array.get(0).getAsJsonObject().get("quantity").getAsInt());
        assertFalse(array.get(0).getAsJsonObject().has("to"));
        assertEquals(4, array.get(1).getAsJsonObject().getAsJsonObject("to").get("shelf").getAsInt());
    }

    @Test
    void readsBinsByZoneAndRejectsATypeMismatch() {
        Map<String, List<BinLocation>> binsByZone = gson.fromJson("""
                {"main": [{"aisle": "A", "shelf": 1, "bin": "A-01-3"}, {"aisle": "A", "shelf": 2, "bin": "A-02-1"}],
                 "cold": [{"aisle": "F", "shelf": 1, "bin": "F-01-1"}]}
                """, new TypeToken<Map<String, List<BinLocation>>>() { }.getType());

        assertEquals(List.of("main", "cold"), List.copyOf(binsByZone.keySet()));
        assertEquals(2, binsByZone.get("main").size());
        assertEquals(2, binsByZone.get("main").get(1).shelf());
        assertEquals("F", binsByZone.get("cold").get(0).aisle());

        JsonSyntaxException mismatch = assertThrows(JsonSyntaxException.class,
                () -> gson.fromJson("{\"warehouse\": \"East\", \"revision\": \"three\"}", Snapshot.class));
        assertTrue(mismatch.getCause() instanceof NumberFormatException, String.valueOf(mismatch.getCause()));
    }

    private static Snapshot sampleSnapshot() {
        StockItem bolt = new StockItem();
        bolt.sku = "BOLT-M8";
        bolt.category = Category.FASTENERS;
        bolt.location = new BinLocation("A", 1, "A-01-3");
        bolt.stockByZone = new LinkedHashMap<>();
        bolt.stockByZone.put("main", 1200);
        bolt.stockByZone.put("overflow", 300);
        bolt.tags = List.of("steel", "zinc-plated");
        bolt.unitPrice = 0.12;
        bolt.serialPolicy = SerialPolicy.NONE;

        StockItem drill = new StockItem();
        drill.sku = "DRILL-18V";
        drill.category = Category.POWER_TOOLS;
        drill.location = new BinLocation("C", 4, "C-04-1");
        drill.stockByZone = new LinkedHashMap<>();
        drill.stockByZone.put("main", 18);
        drill.tags = List.of();
        drill.unitPrice = 149.90;
        drill.serialPolicy = SerialPolicy.TRACKED;

        Snapshot snapshot = new Snapshot();
        snapshot.snapshotId = "snap-0003";
        snapshot.warehouse = "North Depot";
        snapshot.revision = 3;
        snapshot.cacheKey = "in-memory-only";
        snapshot.items = new ArrayList<>(List.of(bolt, drill));
        return snapshot;
    }

    /// Fields every persisted document carries, inherited by the snapshot class.
    static class Document {
        String snapshotId;
    }

    static final class Snapshot extends Document {
        String warehouse;
        int revision;
        String auditor;
        transient String cacheKey;
        List<StockItem> items;
    }

    static final class StockItem {
        String sku;
        Category category;
        BinLocation location;
        Map<String, Integer> stockByZone;
        List<String> tags;
        double unitPrice;
        @SerializedName("serial_policy")
        SerialPolicy serialPolicy;
    }

    record BinLocation(String aisle, int shelf, String bin) {
    }

    record Movement(String sku, int quantity, BinLocation from, BinLocation to) {
    }

    enum Category {
        FASTENERS,
        POWER_TOOLS
    }

    enum SerialPolicy {
        @SerializedName("none") NONE,
        @SerializedName("tracked") TRACKED
    }
}
