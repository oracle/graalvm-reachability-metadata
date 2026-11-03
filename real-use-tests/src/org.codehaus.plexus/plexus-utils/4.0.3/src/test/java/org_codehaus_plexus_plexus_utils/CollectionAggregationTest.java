/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus_plexus_utils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.plexus.util.CollectionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Combines layered application settings with Plexus collection utilities.
 *
 * A deployment tool combines selected-environment overrides with fallback defaults
 * and reports which required capabilities are still unavailable before launching a
 * service.
 */
class CollectionAggregationTest {

    @Test
    void combinesEnvironmentSettingsAndReportsMissingCapabilities() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("endpoint", "https://api.example.test");
        defaults.put("timeout", "30");
        defaults.put("region", "us-east");

        Map<String, String> production = new LinkedHashMap<>();
        production.put("timeout", "10");
        production.put("region", "eu-west");

        Map<String, String> settings = CollectionUtils.mergeMaps(production, defaults);
        Collection<String> requiredCapabilities = List.of("search", "metrics", "tracing");
        Collection<String> availableCapabilities = List.of("search", "metrics");
        Collection<String> missingCapabilities = CollectionUtils.subtract(
                requiredCapabilities, availableCapabilities);

        assertThat(settings)
                .containsEntry("endpoint", "https://api.example.test")
                .containsEntry("timeout", "10")
                .containsEntry("region", "eu-west");
        assertThat(missingCapabilities).containsExactly("tracing");
    }
}
