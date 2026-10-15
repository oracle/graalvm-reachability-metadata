/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The JSON tree model: parsing untyped documents with JsonParser and editing JsonObject,
 * JsonArray, JsonPrimitive, and JsonNull trees.
 *
 * A deployment tool loads a base configuration and an environment overlay as untyped trees,
 * merges them (a JSON null in the overlay deletes a key), audits the result, writes it back,
 * and finally binds the merged tree to the typed settings the service starts with.
 */
public class ConfigOverlayTreeModelTest {

    private static final String BASE = """
            {
              "service": "checkout",
              "http": {"port": 8080, "timeoutSeconds": 2.5, "compression": true},
              "database": {"url": "jdbc:postgresql://db/checkout", "pool": {"min": 2, "max": 10}},
              "features": ["cart", "coupons"],
              "debug": {"sql": true}
            }
            """;

    private static final String PRODUCTION_OVERLAY = """
            {
              "http": {"port": 443, "timeoutSeconds": 1.25},
              "database": {"pool": {"max": 50}, "readReplica": "jdbc:postgresql://replica/checkout"},
              "features": ["cart", "coupons", "gift-cards"],
              "debug": null,
              "price.precision": 12345678901234567890.125
            }
            """;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    @Test
    void mergesOverlayIntoBaseConfiguration(@TempDir Path dir) throws IOException {
        Path baseFile = dir.resolve("base.json");
        Files.writeString(baseFile, BASE, StandardCharsets.UTF_8);
        JsonObject base;
        try (Reader reader = Files.newBufferedReader(baseFile, StandardCharsets.UTF_8)) {
            base = JsonParser.parseReader(reader).getAsJsonObject();
        }
        JsonObject overlay = JsonParser.parseString(PRODUCTION_OVERLAY).getAsJsonObject();

        JsonObject merged = ConfigMerger.merge(base, overlay);

        assertEquals(443, merged.getAsJsonObject("http").get("port").getAsInt());
        assertEquals(1.25, merged.getAsJsonObject("http").get("timeoutSeconds").getAsDouble());
        assertTrue(merged.getAsJsonObject("http").get("compression").getAsBoolean());
        JsonObject pool = merged.getAsJsonObject("database").getAsJsonObject("pool");
        assertEquals(2, pool.get("min").getAsInt());
        assertEquals(50, pool.get("max").getAsInt());
        assertEquals("jdbc:postgresql://replica/checkout",
                merged.getAsJsonObject("database").get("readReplica").getAsString());
        assertFalse(merged.has("debug"));
        assertEquals(3, merged.getAsJsonArray("features").size());
        assertEquals(new BigDecimal("12345678901234567890.125"), merged.get("price.precision").getAsBigDecimal());

        // The merge works on copies: the loaded base document stays untouched.
        assertEquals(8080, base.getAsJsonObject("http").get("port").getAsInt());
        assertTrue(base.has("debug"));
        assertEquals(JsonParser.parseString(BASE), base);

        assertEquals(List.of(
                "database.pool.max", "database.readReplica", "debug", "features",
                "http.port", "http.timeoutSeconds", "price.precision"), ConfigMerger.changedPaths(base, merged));

        Path mergedFile = dir.resolve("production.json");
        try (Writer writer = Files.newBufferedWriter(mergedFile, StandardCharsets.UTF_8)) {
            gson.toJson(merged, writer);
        }
        String written = Files.readString(mergedFile, StandardCharsets.UTF_8);
        assertTrue(written.contains("\n  \"service\": \"checkout\""), written);
        assertEquals(merged, JsonParser.parseString(written));
    }

    @Test
    void editsTreeInPlaceAndBindsItToTypedSettings() {
        JsonObject config = JsonParser.parseString(BASE).getAsJsonObject();
        JsonObject copy = config.deepCopy();
        assertNotSame(config.getAsJsonObject("http"), copy.getAsJsonObject("http"));

        JsonArray features = config.getAsJsonArray("features");
        features.add("loyalty");
        assertTrue(features.remove(new JsonPrimitive("coupons")));
        features.set(0, new JsonPrimitive("basket"));
        config.addProperty("region", "eu-west-1");
        config.add("owner", JsonNull.INSTANCE);
        config.remove("debug");
        config.getAsJsonObject("http").addProperty("port", 9090);

        Map<String, JsonElement> entries = config.asMap();
        assertEquals(List.of("service", "http", "database", "features", "region", "owner"),
                new ArrayList<>(entries.keySet()));
        assertTrue(entries.get("owner").isJsonNull());
        assertEquals(8080, copy.getAsJsonObject("http").get("port").getAsInt());
        assertEquals("[\"basket\",\"loyalty\"]", features.toString());
        assertTrue(config.toString().contains("\"owner\":null"));
        assertFalse(new Gson().toJson(config).contains("owner"));

        ServiceSettings settings = gson.fromJson(config, ServiceSettings.class);
        assertEquals("checkout", settings.service());
        assertEquals("eu-west-1", settings.region());
        assertEquals(9090, settings.http().port());
        assertEquals(2.5, settings.http().timeoutSeconds());
        assertEquals(List.of("basket", "loyalty"), settings.features());
    }

    @Test
    void classifiesPrimitivesAndRejectsMalformedOverlay() {
        JsonArray values = JsonParser.parseString("[42, \"42\", true, null, 0.5, {}, []]").getAsJsonArray();
        List<String> kinds = new ArrayList<>();
        for (JsonElement value : values) {
            kinds.add(ConfigMerger.kind(value));
        }
        assertEquals(List.of("number", "string", "boolean", "null", "number", "object", "array"), kinds);
        assertEquals(42, values.get(1).getAsInt());
        assertEquals(values.get(0).getAsJsonPrimitive().getAsLong(), new JsonPrimitive(42L).getAsLong());

        JsonSyntaxException error = assertThrows(JsonSyntaxException.class,
                () -> JsonParser.parseString("{\"http\": {\"port\": 443,}"));
        assertTrue(error.getMessage().contains("line 1"), error.getMessage());
        assertThrows(IllegalStateException.class,
                () -> JsonParser.parseString("[1, 2]").getAsJsonObject());
    }

    /// Settings the service starts with once the overlay has been applied.
    record ServiceSettings(String service, String region, Http http, List<String> features) {
    }

    record Http(int port, double timeoutSeconds, boolean compression) {
    }

    /// Recursive overlay merge over untyped trees, as a configuration tool would write it.
    static final class ConfigMerger {

        private ConfigMerger() {
        }

        static JsonObject merge(JsonObject base, JsonObject overlay) {
            JsonObject result = base.deepCopy();
            for (Map.Entry<String, JsonElement> entry : overlay.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                JsonElement current = result.get(key);
                if (value.isJsonNull()) {
                    result.remove(key);
                } else if (value.isJsonObject() && current != null && current.isJsonObject()) {
                    result.add(key, merge(current.getAsJsonObject(), value.getAsJsonObject()));
                } else {
                    result.add(key, value.deepCopy());
                }
            }
            return result;
        }

        /// Lists the dotted paths whose value differs between two trees, sorted.
        static List<String> changedPaths(JsonObject before, JsonObject after) {
            List<String> paths = new ArrayList<>();
            collect("", before, after, paths);
            paths.sort(null);
            return paths;
        }

        private static void collect(String prefix, JsonObject before, JsonObject after, List<String> paths) {
            List<String> keys = new ArrayList<>(before.keySet());
            after.keySet().stream().filter(key -> !before.has(key)).forEach(keys::add);
            for (String key : keys) {
                JsonElement left = before.get(key);
                JsonElement right = after.get(key);
                String path = prefix + key;
                if (left != null && right != null && left.isJsonObject() && right.isJsonObject()) {
                    collect(path + ".", left.getAsJsonObject(), right.getAsJsonObject(), paths);
                } else if (left == null || !left.equals(right)) {
                    paths.add(path);
                }
            }
        }

        static String kind(JsonElement value) {
            if (value.isJsonNull()) {
                return "null";
            }
            if (value.isJsonObject()) {
                return "object";
            }
            if (value.isJsonArray()) {
                return "array";
            }
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                return "number";
            }
            return primitive.isBoolean() ? "boolean" : "string";
        }
    }
}
