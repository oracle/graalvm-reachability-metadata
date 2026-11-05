/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_yaml_snakeyaml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yaml.snakeyaml.Yaml;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/// Exercises SnakeYAML's multi-document loading subsystem for release records received as a stream.
public class ReleaseDocumentStreamTest {
    @Test
    @Timeout(60)
    public void loadsEachReleaseRecordFromDeploymentStream() {
        String releaseStream = """
                ---
                service: billing-api
                revision: 41
                environment: production
                ---
                service: billing-api
                revision: 42
                environment: canary
                ---
                service: billing-api
                revision: 43
                environment: production
                """;

        Yaml yaml = new Yaml();
        List<Map<?, ?>> releases = new ArrayList<>();
        for (Object document : yaml.loadAll(new StringReader(releaseStream))) {
            releases.add(assertInstanceOf(Map.class, document));
        }

        assertEquals(3, releases.size());
        assertEquals("billing-api", releases.get(0).get("service"));
        assertEquals(41, releases.get(0).get("revision"));
        assertEquals("production", releases.get(0).get("environment"));
        assertEquals(42, releases.get(1).get("revision"));
        assertEquals("canary", releases.get(1).get("environment"));
        assertEquals(43, releases.get(2).get("revision"));
        assertEquals("production", releases.get(2).get("environment"));
    }
}
