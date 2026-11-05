/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_yaml_snakeyaml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/// Exercises SnakeYAML's deployment-manifest parsing subsystem with defaults, aliases, and a round trip.
public class DeploymentManifestParsingTest {
    @Test
    @Timeout(60)
    public void loadsManifestDefaultsAndPreservesTheEffectiveDeployment() {
        String manifestText = """
                service:
                  name: billing-api
                  replicas: 3
                  ports: [8080, 8443]
                  labels:
                    team: payments
                    tier: backend
                rollout:
                  defaults: &rollout_defaults
                    maxUnavailable: 1
                    maxSurge: 2
                  canary:
                    <<: *rollout_defaults
                    replicas: 1
                """;

        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(loaderOptions);

        Map<?, ?> manifest = asMap(yaml.load(manifestText));
        Map<?, ?> service = asMap(manifest.get("service"));
        Map<?, ?> rollout = asMap(manifest.get("rollout"));
        Map<?, ?> canary = asMap(rollout.get("canary"));

        assertEquals("billing-api", service.get("name"));
        assertEquals(3, service.get("replicas"));
        assertEquals(List.of(8080, 8443), service.get("ports"));
        assertEquals("payments", asMap(service.get("labels")).get("team"));
        assertEquals(1, canary.get("maxUnavailable"));
        assertEquals(2, canary.get("maxSurge"));
        assertEquals(1, canary.get("replicas"));

        String roundTripped = yaml.dump(manifest);
        Map<?, ?> reloadedManifest = asMap(yaml.load(roundTripped));
        assertEquals(manifest, reloadedManifest);
    }

    private static Map<?, ?> asMap(Object value) {
        return assertInstanceOf(Map.class, value);
    }
}
