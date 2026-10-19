/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tree model: reading JSON into `JsonNode` trees, navigating them with `JsonPointer`, mutating
 * `ObjectNode` and `ArrayNode` in place, and converting between trees and typed objects with
 * `treeToValue` and `valueToTree`.
 *
 * A deployment tool keeps a service's configuration as a base JSON document, merges an
 * environment overlay into it field by field, looks settings up by pointer, lists every
 * endpoint URL wherever it is nested, binds the merged result to typed settings, and records an
 * audit entry built from a typed object.
 */
public class ServiceConfigTreeModelTest {

    private static final String BASE = """
            {
              "service": "checkout",
              "replicas": 2,
              "http": {"port": 8080, "timeoutMillis": 1500, "compression": false},
              "upstreams": [
                {"name": "payments", "url": "http://payments.internal:9000"},
                {"name": "inventory", "url": "http://inventory.internal:9100"}
              ],
              "features": ["guest-checkout"],
              "tags": {"team": "orders"}
            }
            """;

    private static final String PRODUCTION_OVERLAY = """
            {
              "replicas": 6,
              "http": {"timeoutMillis": 800, "compression": true},
              "features": ["guest-checkout", "fraud-screening"],
              "tags": {"team": null, "tier": "critical"}
            }
            """;

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void navigatesConfigurationByPointer() throws Exception {
        JsonNode config = mapper.readTree(BASE);

        assertEquals(8080, config.at("/http/port").intValue());
        assertEquals("inventory", config.at("/upstreams/1/name").asText());
        assertEquals(JsonNodeType.ARRAY, config.at("/features").getNodeType());
        assertTrue(config.at("/http/keepAlive").isMissingNode());
        assertEquals(30, config.at("/http/keepAlive").asInt(30));
        assertEquals(1500, config.at(JsonPointer.compile("/http").append(JsonPointer.compile("/timeoutMillis")))
                .longValue());
        assertThrows(IllegalArgumentException.class, () -> config.required("owner"));
    }

    @Test
    void mergesEnvironmentOverlayWithoutTouchingBase() throws Exception {
        ObjectNode base = (ObjectNode) mapper.readTree(BASE);
        ObjectNode overlay = (ObjectNode) mapper.readTree(PRODUCTION_OVERLAY);

        ObjectNode merged = merge(base.deepCopy(), overlay);

        assertEquals(6, merged.get("replicas").intValue());
        assertEquals(8080, merged.at("/http/port").intValue());
        assertEquals(800, merged.at("/http/timeoutMillis").intValue());
        assertTrue(merged.at("/http/compression").booleanValue());
        assertEquals(List.of("guest-checkout", "fraud-screening"), texts(merged.withArray("/features")));
        assertFalse(merged.withObject("/tags").has("team"));
        assertEquals("critical", merged.at("/tags/tier").asText());
        assertEquals(2, base.get("replicas").intValue());
        assertEquals("orders", base.at("/tags/team").asText());
        assertNotEquals(base, merged);
        assertEquals(mapper.readTree(BASE), base);
    }

    @Test
    void buildsAndEditsConfigurationInPlace() throws Exception {
        ObjectNode config = mapper.createObjectNode();
        config.put("service", "search");
        config.withObject("/http").put("port", 7000);
        config.withObject("/limits/requests").put("perSecond", 250);
        ArrayNode upstreams = config.putArray("upstreams");
        upstreams.addObject().put("name", "index").put("url", "http://index.internal:9200");
        upstreams.addObject().put("name", "suggest").put("url", "http://suggest.internal:9300");
        upstreams.insert(0, mapper.createObjectNode().put("name", "cache").put("url", "http://cache.internal:6379"));

        upstreams.remove(2);
        ((ObjectNode) upstreams.get(1)).set("url", TextNode.valueOf("https://index.internal:9243"));
        JsonNode previous = config.replace("service", TextNode.valueOf("search-v2"));

        assertEquals("search", previous.asText());
        assertEquals("""
                {"service":"search-v2","http":{"port":7000},"limits":{"requests":{"perSecond":250}},\
                "upstreams":[{"name":"cache","url":"http://cache.internal:6379"},\
                {"name":"index","url":"https://index.internal:9243"}]}""", mapper.writeValueAsString(config));
    }

    @Test
    void findsEveryEndpointUrlWhereverItIsNested() throws Exception {
        JsonNode config = mapper.readTree("""
                {
                  "upstreams": [{"name": "payments", "url": "http://payments.internal:9000"}],
                  "observability": {
                    "tracing": {"url": "http://jaeger.internal:14268"},
                    "metrics": {"push": {"url": "http://pushgateway.internal:9091"}}
                  }
                }
                """);

        List<String> urls = config.findValuesAsText("url");
        JsonNode metrics = config.findParent("push");
        List<String> fieldNames = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = config.get("observability").fields();
        while (fields.hasNext()) {
            fieldNames.add(fields.next().getKey());
        }

        assertEquals(List.of("http://payments.internal:9000", "http://jaeger.internal:14268",
                "http://pushgateway.internal:9091"), urls);
        assertTrue(metrics.has("push"));
        assertEquals(List.of("tracing", "metrics"), fieldNames);
    }

    @Test
    void bindsMergedTreeToTypedSettings() throws Exception {
        ObjectNode merged = merge((ObjectNode) mapper.readTree(BASE), (ObjectNode) mapper.readTree(PRODUCTION_OVERLAY));

        ServiceSettings settings = mapper.treeToValue(merged, ServiceSettings.class);
        HttpSettings http = mapper.treeToValue(merged.get("http"), HttpSettings.class);

        assertEquals("checkout", settings.service());
        assertEquals(6, settings.replicas());
        assertEquals(new HttpSettings(8080, 800, true), settings.http());
        assertEquals(settings.http(), http);
        assertEquals(List.of(new Upstream("payments", "http://payments.internal:9000"),
                new Upstream("inventory", "http://inventory.internal:9100")), settings.upstreams());
        assertEquals(List.of("guest-checkout", "fraud-screening"), settings.features());
        assertEquals(Map.of("tier", "critical"), settings.tags());
    }

    @Test
    void recordsAuditEntryBuiltFromTypedChange() throws Exception {
        ConfigChange change = new ConfigChange("checkout", "/http/timeoutMillis", 1500, 800, "release-42");

        ObjectNode entry = mapper.valueToTree(change);
        entry.put("approved", true);
        entry.remove("ticket");
        JsonNode reread = mapper.readTree(mapper.writeValueAsBytes(entry));

        assertInstanceOf(ObjectNode.class, reread);
        assertEquals(entry, reread);
        assertEquals("/http/timeoutMillis", reread.get("pointer").asText());
        assertTrue(reread.get("from").isInt());
        assertEquals(-700, reread.get("to").intValue() - reread.get("from").intValue());
        assertFalse(reread.has("ticket"));
        assertTrue(reread.get("approved").booleanValue());
    }

    /// Copies overlay fields onto `target`: objects merge recursively, `null` deletes a field,
    /// anything else replaces it.
    private static ObjectNode merge(ObjectNode target, ObjectNode overlay) {
        Iterator<Map.Entry<String, JsonNode>> fields = overlay.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode existing = target.get(field.getKey());
            JsonNode value = field.getValue();
            if (value.isNull()) {
                target.remove(field.getKey());
            } else if (value.isObject() && existing != null && existing.isObject()) {
                merge((ObjectNode) existing, (ObjectNode) value);
            } else {
                target.set(field.getKey(), value.deepCopy());
            }
        }
        return target;
    }

    private static List<String> texts(ArrayNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(element -> values.add(element.asText()));
        return values;
    }

    public record ServiceSettings(
            String service,
            int replicas,
            HttpSettings http,
            List<Upstream> upstreams,
            List<String> features,
            Map<String, String> tags) {
    }

    public record HttpSettings(int port, int timeoutMillis, boolean compression) {
    }

    public record Upstream(String name, String url) {
    }

    public record ConfigChange(String service, String pointer, int from, int to, String ticket) {
    }
}
