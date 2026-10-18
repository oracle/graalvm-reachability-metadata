/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.module.kotlin.ExtensionsKt;
import com.fasterxml.jackson.module.kotlin.KotlinFeature;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import kotlin.Pair;
import kotlin.Unit;
import kotlin.jvm.JvmClassMappingKt;
import org.junit.jupiter.api.Test;

/// Mapper construction and JSON-tree editing through the Kotlin module's extension functions
/// (`jacksonMapperBuilder`, `jsonMapper`, `kotlinModule`, `registerKotlinModule`,
/// `SimpleModule.addSerializer(KClass)`, and the `ArrayNode`/`ObjectNode` operators).
///
/// A Java inventory service shares stock snapshots with Kotlin warehouse clients. It builds its
/// mappers with the helpers the Kotlin module ships instead of wiring `KotlinModule` by hand,
/// registers its quantity codec once per Kotlin class so it covers both `int` and `Integer`
/// properties, and edits outgoing JSON patches with the module's tree operators.
class KotlinMapperExtensionsTest {

    /// One stock line as the warehouse clients exchange it.
    public static final class StockSnapshot {
        public String sku;
        public int onHand;
        public Integer reserved;
        public Pair<String, String> bin;
        public Unit acknowledged;
    }

    /// Writes a quantity as the text `"<n> pcs"` that the warehouse displays verbatim.
    public static final class QuantitySerializer extends StdSerializer<Integer> {
        QuantitySerializer() {
            super(Integer.class);
        }

        @Override
        public void serialize(Integer value, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            generator.writeString(value + " pcs");
        }
    }

    /// Reads a quantity written as `"<n> pcs"` back into a number.
    public static final class QuantityDeserializer extends StdDeserializer<Integer> {
        QuantityDeserializer() {
            super(Integer.class);
        }

        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String text = parser.getValueAsString();
            return Integer.valueOf(text.substring(0, text.length() - " pcs".length()));
        }
    }

    private static SimpleModule quantityModule() {
        SimpleModule module = new SimpleModule("quantities");
        ExtensionsKt.addSerializer(module, JvmClassMappingKt.getKotlinClass(Integer.class),
                new QuantitySerializer());
        ExtensionsKt.addDeserializer(module, JvmClassMappingKt.getKotlinClass(Integer.class),
                new QuantityDeserializer());
        return module;
    }

    private static final TypeReference<Pair<String, String>> BIN = new TypeReference<>() {
    };

    @Test
    void buildsTheSnapshotMapperFromTheKotlinModuleBuilderHelper() throws Exception {
        JsonMapper mapper = ExtensionsKt.jacksonMapperBuilder(kotlin -> {
            kotlin.enable(KotlinFeature.SingletonSupport);
            return Unit.INSTANCE;
        }).addModule(quantityModule()).build();

        StockSnapshot snapshot = new StockSnapshot();
        snapshot.sku = "BOLT-M8";
        snapshot.onHand = 1200;
        snapshot.reserved = 75;
        snapshot.bin = new Pair<>("aisle-4", "shelf-2");
        snapshot.acknowledged = Unit.INSTANCE;

        String json = mapper.writeValueAsString(snapshot);

        JsonNode tree = mapper.readTree(json);
        assertEquals("1200 pcs", tree.get("onHand").asText());
        assertEquals("75 pcs", tree.get("reserved").asText());
        assertEquals("aisle-4", tree.get("bin").get("first").asText());
        assertEquals("shelf-2", tree.get("bin").get("second").asText());

        StockSnapshot replayed = mapper.readValue(json, StockSnapshot.class);
        assertEquals("BOLT-M8", replayed.sku);
        assertEquals(1200, replayed.onHand);
        assertEquals(75, replayed.reserved);
        assertEquals(new Pair<>("aisle-4", "shelf-2"), replayed.bin);
        assertSame(Unit.INSTANCE, replayed.acknowledged);
    }

    @Test
    void nestsAConfiguredKotlinModuleInsideTheJsonMapperHelper() throws Exception {
        JsonMapper mapper = ExtensionsKt.jsonMapper(json -> {
            json.addModule(ExtensionsKt.kotlinModule(kotlin -> {
                kotlin.enable(KotlinFeature.SingletonSupport);
                return Unit.INSTANCE;
            }));
            return Unit.INSTANCE;
        });

        assertSame(Unit.INSTANCE, mapper.readValue("{}", Unit.class));
        assertEquals(new Pair<>("dock-1", "pallet-9"), mapper.readValue("""
                {"first": "dock-1", "second": "pallet-9"}
                """, BIN));
    }

    @Test
    void retrofitsTheKotlinModuleOntoAnExistingMapper() throws Exception {
        String bin = """
                {"first": "aisle-7", "second": "shelf-1"}
                """;
        assertThrows(InvalidDefinitionException.class, () -> new ObjectMapper().readValue(bin, BIN));

        ObjectMapper legacy = new ObjectMapper();
        ObjectMapper upgraded = ExtensionsKt.registerKotlinModule(legacy);
        assertSame(legacy, upgraded);
        assertEquals(new Pair<>("aisle-7", "shelf-1"), upgraded.readValue(bin, BIN));

        ObjectMapper fresh = ExtensionsKt.jacksonObjectMapper();
        assertEquals(new Pair<>("aisle-7", "shelf-1"), fresh.readValue(bin, BIN));
        assertTrue(fresh.getRegisteredModuleIds().containsAll(upgraded.getRegisteredModuleIds()));
    }

    @Test
    void editsAnOutgoingPatchWithTheTreeOperators() throws Exception {
        ObjectMapper mapper = ExtensionsKt.jacksonObjectMapper();
        ObjectNode patch = (ObjectNode) mapper.readTree("""
                {"sku": "BOLT-M8", "draft": true, "editor": "ana", "note": "recount",
                 "tags": ["fastener"]}
                """);

        ArrayNode tags = (ArrayNode) patch.get("tags");
        ExtensionsKt.plusAssign(tags, "steel");
        ExtensionsKt.plus(tags, 8);
        ExtensionsKt.plusAssign(tags, true);
        ExtensionsKt.plusAssign(tags, new BigDecimal("0.25"));
        ExtensionsKt.plusAssign(tags, mapper.readTree("{\"bulk\": 500}"));
        ExtensionsKt.plusAssign(tags, (ArrayNode) mapper.readTree("[\"m8\", \"zinc\"]"));
        ExtensionsKt.minusAssign(tags, 2);

        ExtensionsKt.minusAssign(patch, "draft");
        ExtensionsKt.minus(patch, List.of("editor", "note"));

        assertTrue(ExtensionsKt.contains(patch, "sku"));
        assertTrue(ExtensionsKt.contains(patch, "tags"));
        assertFalse(ExtensionsKt.contains(patch, "draft"));
        assertFalse(ExtensionsKt.contains(patch, "editor"));
        assertTrue(ExtensionsKt.contains(tags, 6));
        assertFalse(ExtensionsKt.contains(tags, 7));

        assertEquals("""
                {"sku":"BOLT-M8","tags":["fastener","steel",true,0.25,{"bulk":500},"m8","zinc"]}""",
                mapper.writeValueAsString(patch));
    }
}
