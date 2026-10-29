/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_api;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.api.utils.json.NacosTypeReference;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class JsonUtilsTest {
    @Test
    void readsAndRoundTripsServiceRegistration() {
        Instance instance = JsonUtils.toObj("""
                {"ip":"192.0.2.30","port":8080,"serviceName":"orders","clusterName":"east",
                 "weight":2.5,"healthy":false,"metadata":{"zone":"east"}}
                """, Instance.class);

        assertEquals("192.0.2.30:8080", instance.toInetAddr());
        assertEquals("orders", instance.getServiceName());
        assertEquals("east", instance.getClusterName());
        assertEquals(2.5, instance.getWeight());
        assertFalse(instance.isHealthy());
        assertEquals(Map.of("zone", "east"), instance.getMetadata());
        assertEquals(instance, JsonUtils.toObj(JsonUtils.toJson(instance), Instance.class));
        assertEquals(instance, JsonUtils.toObj(JsonUtils.toJsonBytes(instance), Instance.class));
    }

    @Test
    void readsTypedCollectionsFromTextBytesAndStreams() throws Exception {
        String json = """
                [{"ip":"192.0.2.31","port":8081,"metadata":{"zone":"west"}}]
                """;
        NacosTypeReference<List<Instance>> type = new NacosTypeReference<List<Instance>>() { };
        List<Instance> fromText = JsonUtils.toObj(json, type);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        List<Instance> fromBytes = JsonUtils.toObj(bytes, type);
        List<Instance> fromStream;
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            fromStream = JsonUtils.toObj(input, type.getType());
        }

        assertEquals(1, fromText.size());
        assertEquals("192.0.2.31:8081", fromText.get(0).toInetAddr());
        assertEquals("west", fromText.get(0).getMetadata().get("zone"));
        assertEquals(fromText, fromBytes);
        assertEquals(fromText, fromStream);
    }

    @Test
    void canonicalizesNestedMapKeys() {
        Map<String, Object> document = Map.of("zone", "east", "attributes", Map.of("z", 2, "a", 1));
        assertEquals("""
                {"attributes":{"a":1,"z":2},"zone":"east"}""", JsonUtils.toCanonicalJson(document));
    }
}
