/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.core.config.plugins.util.PluginRegistry;
import org.apache.logging.log4j.core.config.plugins.util.PluginType;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PluginRegistryTest {
    @Test
    void resolvesBuiltInPluginsFromTheClasspathCache() {
        PluginRegistry registry = PluginRegistry.getInstance();
        try {
            Map<String, List<PluginType<?>>> categories = registry.loadFromBundle(101L, getClass().getClassLoader());
            PluginType<?> plugin = categories.get("core").stream()
                    .filter(type -> type.getKey().equals("patternlayout")).findFirst().orElseThrow();
            assertEquals(PatternLayout.class, plugin.getPluginClass());
            assertNotNull(registry.getPluginsByCategoryByBundleId().get(101L));
            assertInstanceOf(PatternLayout.class, PatternLayout.newBuilder().withPattern("%m").build());
        } finally {
            registry.clearBundlePlugins(101L);
        }
    }
}
