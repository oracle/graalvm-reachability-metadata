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
import org.yaml.snakeyaml.events.DocumentStartEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.StreamEndEvent;
import org.yaml.snakeyaml.events.StreamStartEvent;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises SnakeYAML's event-stream parsing subsystem for indexing multi-document configuration.
public class ConfigurationEventStreamTest {
    @Test
    @Timeout(60)
    public void indexesReleaseDocumentsFromConfigurationStream() {
        String configurationStream = """
                ---
                service: billing-api
                revision: 7
                environment: production
                ...
                ---
                service: payments-api
                revision: 8
                environment: canary
                ...
                """;

        Yaml yaml = new Yaml();
        List<Event> events = new ArrayList<>();
        yaml.parse(new StringReader(configurationStream)).forEach(events::add);

        assertInstanceOf(StreamStartEvent.class, events.get(0));
        assertInstanceOf(StreamEndEvent.class, events.get(events.size() - 1));
        List<ReleaseSummary> releases = indexReleases(events);

        assertEquals(List.of(
                new ReleaseSummary("billing-api", "7", "production"),
                new ReleaseSummary("payments-api", "8", "canary")), releases);
        assertTrue(events.stream().filter(DocumentStartEvent.class::isInstance)
                .map(DocumentStartEvent.class::cast)
                .allMatch(DocumentStartEvent::getExplicit));
    }

    private static List<ReleaseSummary> indexReleases(List<Event> events) {
        List<ReleaseSummary> releases = new ArrayList<>();
        String service = null;
        String revision = null;
        String environment = null;
        String field = null;
        for (Event event : events) {
            if (event instanceof MappingStartEvent) {
                field = null;
            } else if (event instanceof ScalarEvent scalar) {
                if (field == null) {
                    field = scalar.getValue();
                } else if (field.equals("service")) {
                    service = scalar.getValue();
                    field = null;
                } else if (field.equals("revision")) {
                    revision = scalar.getValue();
                    field = null;
                } else if (field.equals("environment")) {
                    environment = scalar.getValue();
                    field = null;
                }
            } else if (event instanceof MappingEndEvent) {
                releases.add(new ReleaseSummary(service, revision, environment));
                service = null;
                revision = null;
                environment = null;
            }
        }
        return releases;
    }

    private record ReleaseSummary(String service, String revision, String environment) {
    }
}
