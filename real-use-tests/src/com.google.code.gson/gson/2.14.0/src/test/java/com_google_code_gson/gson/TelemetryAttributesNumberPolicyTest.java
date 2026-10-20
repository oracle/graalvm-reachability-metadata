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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.FormattingStyle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.LongSerializationPolicy;
import com.google.gson.ToNumberPolicy;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Number and null handling policies for schemaless payloads: `ToNumberPolicy` strategies,
 * `LongSerializationPolicy`, special floating-point values, and `serializeNulls`.
 *
 * A telemetry ingestion service accepts free-form attribute bags from agents as
 * `Map<String, Object>`, so Gson must decide on its own which Java number type each JSON number
 * becomes. The service keeps 64-bit trace IDs exact, books billing amounts as `BigDecimal`,
 * forwards attributes to downstream collectors without rewriting their number text, sends IDs to
 * a browser dashboard as strings, and exports sensor readings that may be `NaN` or infinite while
 * keeping absent units as explicit `null`s.
 */
public class TelemetryAttributesNumberPolicyTest {

    private static final Type ATTRIBUTES = new TypeToken<Map<String, Object>>() { }.getType();

    /// Larger than 2^53, so a `double` cannot represent it exactly.
    private static final long TRACE_ID = 9_007_199_254_740_993L;

    private static final String AGENT_PAYLOAD = """
            {
              "traceId": 9007199254740993,
              "durationMs": 12.5,
              "retries": 3,
              "host": "edge-7",
              "tags": {"shard": 4, "weight": 0.25},
              "samples": [1, 2.5, 3]
            }
            """;

    @Test
    void defaultObjectStrategyLosesTraceIdPrecision() {
        Map<String, Object> attributes = new Gson().fromJson(AGENT_PAYLOAD, ATTRIBUTES);

        Object traceId = attributes.get("traceId");
        assertInstanceOf(Double.class, traceId);
        assertNotEquals(TRACE_ID, ((Double) traceId).longValue());
        assertEquals(3.0, attributes.get("retries"));
    }

    @Test
    void longOrDoublePolicyKeepsIntegersExact() {
        Gson gson = new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create();

        Map<String, Object> attributes = gson.fromJson(AGENT_PAYLOAD, ATTRIBUTES);

        assertEquals(TRACE_ID, attributes.get("traceId"));
        assertEquals(12.5, attributes.get("durationMs"));
        assertEquals(3L, attributes.get("retries"));
        assertEquals("edge-7", attributes.get("host"));

        Map<?, ?> tags = assertInstanceOf(Map.class, attributes.get("tags"));
        assertEquals(4L, tags.get("shard"));
        assertEquals(0.25, tags.get("weight"));

        List<?> samples = assertInstanceOf(List.class, attributes.get("samples"));
        assertEquals(List.of(1L, 2.5, 3L), samples);

        String reExported = gson.toJson(attributes);
        assertTrue(reExported.contains("\"traceId\":9007199254740993"), reExported);
        assertTrue(reExported.contains("\"retries\":3,"), reExported);
    }

    @Test
    void bigDecimalPolicyBooksBillingAmountsExactly() {
        Gson gson = new GsonBuilder()
                .setObjectToNumberStrategy(ToNumberPolicy.BIG_DECIMAL)
                .setNumberToNumberStrategy(ToNumberPolicy.BIG_DECIMAL)
                .create();

        String usage = """
                {"tenant": "acme", "units": 3, "unitPrice": 0.1, "credit": 0.30}
                """;
        Map<String, Object> attributes = gson.fromJson(usage, ATTRIBUTES);
        BigDecimal units = assertInstanceOf(BigDecimal.class, attributes.get("units"));
        BigDecimal unitPrice = assertInstanceOf(BigDecimal.class, attributes.get("unitPrice"));
        BigDecimal credit = assertInstanceOf(BigDecimal.class, attributes.get("credit"));
        assertEquals(0, units.multiply(unitPrice).compareTo(credit));

        BillingLine line = gson.fromJson("""
                {"sku": "ingest-gb", "amount": 1234567890.123456789}
                """, BillingLine.class);
        assertEquals("ingest-gb", line.sku);
        assertEquals(new BigDecimal("1234567890.123456789"), line.amount);
    }

    @Test
    void lazilyParsedNumbersForwardTheOriginalNumberText() {
        Gson gson = new GsonBuilder()
                .setObjectToNumberStrategy(ToNumberPolicy.LAZILY_PARSED_NUMBER)
                .create();

        String reading = """
                {"sensor":"t-1","celsius":21.50,"counter":123456789012345678901234567890}
                """.strip();
        Map<String, Object> attributes = gson.fromJson(reading, ATTRIBUTES);

        Number celsius = assertInstanceOf(Number.class, attributes.get("celsius"));
        assertEquals("21.50", celsius.toString());
        assertEquals(21.5, celsius.doubleValue());
        Number counter = assertInstanceOf(Number.class, attributes.get("counter"));
        assertEquals("123456789012345678901234567890", counter.toString());

        String forwarded = gson.toJson(attributes);
        assertEquals(reading, forwarded);
    }

    @Test
    void stringLongPolicySendsIdsSafelyToBrowserClients() {
        Gson dashboardGson = new GsonBuilder()
                .setLongSerializationPolicy(LongSerializationPolicy.STRING)
                .create();

        Span span = new Span(TRACE_ID, 42L, "checkout", 87);
        JsonObject json = JsonParser.parseString(dashboardGson.toJson(span)).getAsJsonObject();

        assertTrue(json.get("traceId").getAsJsonPrimitive().isString());
        assertEquals("9007199254740993", json.get("traceId").getAsString());
        assertEquals("42", json.get("parentId").getAsString());
        assertTrue(json.get("durationMs").getAsJsonPrimitive().isNumber());

        Span echoed = dashboardGson.fromJson(json, Span.class);
        assertEquals(span, echoed);

        Span root = new Span(TRACE_ID, null, "gateway", 120);
        JsonObject rootJson = dashboardGson.toJsonTree(root).getAsJsonObject();
        assertTrue(rootJson.has("traceId"));
        assertFalse(rootJson.has("parentId"));
    }

    @Test
    void sensorExportWritesSpecialFloatsAndExplicitNulls() {
        SensorReading[] readings = {
            new SensorReading("probe-a", Double.NaN, null),
            new SensorReading("probe-b", Double.POSITIVE_INFINITY, "kPa"),
            new SensorReading("probe-c", -40.0, "C"),
        };

        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> new Gson().toJson(readings));
        assertTrue(rejected.getMessage().contains("NaN"), rejected.getMessage());

        Gson exportGson = new GsonBuilder()
                .serializeSpecialFloatingPointValues()
                .serializeNulls()
                .setFormattingStyle(FormattingStyle.PRETTY.withIndent("  ").withNewline("\n"))
                .create();

        String export = exportGson.toJson(readings);
        assertEquals("""
                [
                  {
                    "sensor": "probe-a",
                    "value": NaN,
                    "unit": null
                  },
                  {
                    "sensor": "probe-b",
                    "value": Infinity,
                    "unit": "kPa"
                  },
                  {
                    "sensor": "probe-c",
                    "value": -40.0,
                    "unit": "C"
                  }
                ]""", export);

        SensorReading[] imported = exportGson.fromJson(export, SensorReading[].class);
        assertEquals(3, imported.length);
        assertTrue(Double.isNaN(imported[0].value));
        assertNull(imported[0].unit);
        assertEquals(Double.POSITIVE_INFINITY, imported[1].value);
        assertEquals("kPa", imported[1].unit);
        assertEquals(-40.0, imported[2].value);
    }

    /// One line of a tenant's bill; the amount's JSON number is mapped by the number strategy.
    static final class BillingLine {
        String sku;
        Number amount;
    }

    /// A tracing span as the dashboard API returns it; `parentId` is `null` for root spans.
    record Span(long traceId, Long parentId, String operation, int durationMs) {
    }

    /// One sensor value in the nightly export.
    static final class SensorReading {
        String sensor;
        double value;
        String unit;

        SensorReading() {
        }

        SensorReading(String sensor, double value, String unit) {
            this.sensor = sensor;
            this.value = value;
            this.unit = unit;
        }
    }
}
