/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_yaml_snakeyaml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises SnakeYAML's multi-document serialization subsystem for deployment bundles.
public class DeploymentBundleSerializationTest {
    @Test
    @Timeout(60)
    public void serializesDeploymentBundleForReleasePromotion() {
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setExplicitStart(true);
        dumperOptions.setExplicitEnd(true);
        dumperOptions.setIndent(2);

        Yaml yaml = new Yaml(dumperOptions);
        List<Map<String, Object>> deploymentBundle = List.of(
                deployment("billing-api", "production", 3),
                deployment("payments-api", "canary", 1));

        String serialized = yaml.dumpAll(deploymentBundle.iterator());

        assertTrue(serialized.startsWith("---\n"));
        assertEquals(2, serialized.split("---", -1).length - 1);
        assertTrue(serialized.contains("service: billing-api"));
        assertTrue(serialized.contains("service: payments-api"));
        assertTrue(serialized.contains("...\n"));

        List<Map<?, ?>> reloaded = new ArrayList<>();
        Iterator<Object> documents = yaml.loadAll(serialized).iterator();
        while (documents.hasNext()) {
            reloaded.add((Map<?, ?>) documents.next());
        }

        assertEquals(deploymentBundle, reloaded);
    }

    private static Map<String, Object> deployment(String service, String environment, int replicas) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("service", service);
        document.put("environment", environment);
        document.put("replicas", replicas);
        return document;
    }
}
