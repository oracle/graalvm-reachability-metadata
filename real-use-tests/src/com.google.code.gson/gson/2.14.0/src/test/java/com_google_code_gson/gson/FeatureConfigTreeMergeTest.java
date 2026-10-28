/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The JSON tree model: building, copying, mutating, comparing, and binding `JsonElement` trees.
 *
 * A feature-flag service keeps its typed defaults in code, converts them to a tree with
 * `Gson.toJsonTree`, and layers per-environment overrides on top as RFC 7396 merge patches
 * applied to `JsonObject`s. The merged tree is bound back to the typed configuration with
 * `Gson.fromJson(JsonElement, Class)`. To roll out a change the service diffs two trees into a
 * minimal patch, and it rewrites rollout arrays in place before publishing the document.
 */
public class FeatureConfigTreeMergeTest {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    @Test
    void typedDefaultsBecomeATreeThatMirrorsTheModel() {
        JsonObject tree = GSON.toJsonTree(ServiceConfig.defaults()).getAsJsonObject();

        assertEquals(Set.of("service", "maxConnections", "requestTimeoutSeconds", "flags", "regions"),
                tree.keySet());
        JsonPrimitive timeout = tree.getAsJsonPrimitive("requestTimeoutSeconds");
        assertTrue(timeout.isNumber());
        assertEquals(new BigDecimal("2.5"), timeout.getAsBigDecimal());
        assertTrue(tree.getAsJsonObject("flags").getAsJsonObject("darkMode").get("enabled")
                .getAsJsonPrimitive().isBoolean());
        assertEquals(new JsonArray(), tree.getAsJsonObject("flags").getAsJsonObject("darkMode")
                .getAsJsonArray("audiences"));
        assertEquals(List.of("eu-west", "us-east"), strings(tree.getAsJsonArray("regions")));
    }

    @Test
    void environmentOverlaysMergeIntoDefaultsAndBindBackToTheModel() throws IOException {
        JsonObject defaults = GSON.toJsonTree(ServiceConfig.defaults()).getAsJsonObject();
        String productionOverlay = """
                {
                  "maxConnections": 200,
                  "flags": {
                    "darkMode": {"enabled": true, "audiences": ["beta", "staff"]},
                    "legacyCheckout": null,
                    "newSearch": {"enabled": true, "audiences": ["staff"]}
                  },
                  "regions": ["eu-west", "us-east", "ap-south"]
                }
                """;

        JsonObject production = defaults.deepCopy();
        try (Reader reader = new StringReader(productionOverlay)) {
            ConfigMerger.apply(production, JsonParser.parseReader(reader));
        }

        assertEquals(5, defaults.getAsJsonObject("flags").getAsJsonObject("legacyCheckout")
                .get("rolloutPercent").getAsInt(), "the defaults tree must stay untouched");
        assertFalse(defaults.getAsJsonObject("flags").has("newSearch"));

        ServiceConfig config = GSON.fromJson(production, ServiceConfig.class);
        assertEquals("catalog", config.service);
        assertEquals(200, config.maxConnections);
        assertEquals(2.5, config.requestTimeoutSeconds);
        assertEquals(Set.of("darkMode", "newSearch"), config.flags.keySet());
        assertTrue(config.flags.get("darkMode").enabled);
        assertEquals(List.of("beta", "staff"), config.flags.get("darkMode").audiences);
        assertEquals(0, config.flags.get("darkMode").rolloutPercent,
                "fields absent from the overlay keep their default value");
        assertEquals(List.of("staff"), config.flags.get("newSearch").audiences);
        assertEquals(List.of("eu-west", "us-east", "ap-south"), config.regions);
    }

    @Test
    void diffOfTwoTreesIsAMinimalPatchThatReproducesTheTarget() {
        JsonObject current = GSON.toJsonTree(ServiceConfig.defaults()).getAsJsonObject();
        ServiceConfig next = ServiceConfig.defaults();
        next.maxConnections = 64;
        next.flags.remove("legacyCheckout");
        next.flags.get("darkMode").rolloutPercent = 25;
        JsonObject target = GSON.toJsonTree(next).getAsJsonObject();

        JsonObject patch = ConfigMerger.diff(current, target);

        assertEquals(JsonParser.parseString("""
                {
                  "maxConnections": 64,
                  "flags": {"darkMode": {"rolloutPercent": 25}, "legacyCheckout": null}
                }
                """), patch);
        assertEquals("{\"maxConnections\":64,\"flags\":"
                + "{\"darkMode\":{\"rolloutPercent\":25},\"legacyCheckout\":null}}", GSON.toJson(patch));

        JsonObject rolledOut = current.deepCopy();
        ConfigMerger.apply(rolledOut, patch);
        assertEquals(target, rolledOut);
        assertNotEquals(current, rolledOut);
        assertEquals(new JsonObject(), ConfigMerger.diff(target, rolledOut));
    }

    @Test
    void rolloutArraysAreRewrittenInPlaceBeforePublishing() {
        JsonObject document = GSON.toJsonTree(ServiceConfig.defaults()).getAsJsonObject();
        JsonArray audiences = document.getAsJsonObject("flags").getAsJsonObject("legacyCheckout")
                .getAsJsonArray("audiences");

        audiences.add("partners");
        audiences.add(JsonNull.INSTANCE);
        audiences.set(0, new JsonPrimitive("internal"));
        assertTrue(audiences.remove(new JsonPrimitive("partners")));
        audiences.remove(audiences.size() - 1);
        audiences.add(new JsonPrimitive("beta"));

        JsonArray regions = document.getAsJsonArray("regions");
        JsonArray sortedRegions = new JsonArray();
        List<String> names = strings(regions);
        names.sort(null);
        names.reversed().forEach(sortedRegions::add);
        document.add("regions", sortedRegions);
        document.addProperty("publishedBy", "release-bot");
        document.remove("requestTimeoutSeconds");

        String published = GSON.toJson(document);
        JsonObject reparsed = JsonParser.parseString(published).getAsJsonObject();
        assertEquals(document, reparsed);
        assertEquals(List.of("internal", "beta"), strings(reparsed.getAsJsonObject("flags")
                .getAsJsonObject("legacyCheckout").getAsJsonArray("audiences")));
        assertEquals(List.of("us-east", "eu-west"), strings(reparsed.getAsJsonArray("regions")));
        assertEquals("release-bot", reparsed.get("publishedBy").getAsString());
        assertFalse(reparsed.has("requestTimeoutSeconds"));

        ServiceConfig bound = GSON.fromJson(reparsed, ServiceConfig.class);
        assertEquals(0.0, bound.requestTimeoutSeconds, "a removed member binds to the field default");
        assertEquals(List.of("internal", "beta"), bound.flags.get("legacyCheckout").audiences);
    }

    private static List<String> strings(JsonArray array) {
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            values.add(element.getAsString());
        }
        return values;
    }

    /// RFC 7396 JSON merge patch over Gson trees.
    static final class ConfigMerger {

        private ConfigMerger() {
        }

        /// Applies `patch` to `target` in place: `null` members delete, objects merge, all else replaces.
        static void apply(JsonObject target, JsonElement patch) {
            for (Map.Entry<String, JsonElement> member : patch.getAsJsonObject().entrySet()) {
                String name = member.getKey();
                JsonElement value = member.getValue();
                if (value.isJsonNull()) {
                    target.remove(name);
                } else if (value.isJsonObject()) {
                    JsonElement existing = target.get(name);
                    JsonObject merged = existing != null && existing.isJsonObject()
                            ? existing.getAsJsonObject()
                            : new JsonObject();
                    apply(merged, value);
                    target.add(name, merged);
                } else {
                    target.add(name, value.deepCopy());
                }
            }
        }

        /// Computes the smallest merge patch that turns `source` into `target`.
        static JsonObject diff(JsonObject source, JsonObject target) {
            JsonObject patch = new JsonObject();
            Set<String> names = new LinkedHashSet<>(source.keySet());
            names.addAll(target.keySet());
            for (String name : names) {
                JsonElement before = source.get(name);
                JsonElement after = target.get(name);
                if (after == null) {
                    patch.add(name, JsonNull.INSTANCE);
                } else if (before != null && before.isJsonObject() && after.isJsonObject()) {
                    JsonObject nested = diff(before.getAsJsonObject(), after.getAsJsonObject());
                    if (!nested.isEmpty()) {
                        patch.add(name, nested);
                    }
                } else if (!after.equals(before)) {
                    patch.add(name, after.deepCopy());
                }
            }
            return patch;
        }
    }

    /// Typed service configuration with code-level defaults.
    static final class ServiceConfig {
        String service;
        int maxConnections;
        double requestTimeoutSeconds;
        Map<String, Flag> flags;
        List<String> regions;

        static ServiceConfig defaults() {
            ServiceConfig config = new ServiceConfig();
            config.service = "catalog";
            config.maxConnections = 32;
            config.requestTimeoutSeconds = 2.5;
            config.flags = new LinkedHashMap<>();
            config.flags.put("darkMode", new Flag(false, 0, new ArrayList<>()));
            config.flags.put("legacyCheckout", new Flag(true, 5, new ArrayList<>(List.of("staff"))));
            config.regions = new ArrayList<>(List.of("eu-west", "us-east"));
            return config;
        }
    }

    /// One feature flag: whether it is on, for what share of traffic, and for which audiences.
    static final class Flag {
        boolean enabled;
        int rolloutPercent;
        List<String> audiences;

        Flag() {
        }

        Flag(boolean enabled, int rolloutPercent, List<String> audiences) {
            this.enabled = enabled;
            this.rolloutPercent = rolloutPercent;
            this.audiences = audiences;
        }
    }
}
