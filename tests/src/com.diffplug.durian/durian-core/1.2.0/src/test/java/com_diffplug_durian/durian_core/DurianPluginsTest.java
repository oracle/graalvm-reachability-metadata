/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_core;

import com.diffplug.common.base.DurianPlugins;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DurianPluginsTest {
    @Test
    void loadsConfiguredPluginImplementationFromSystemProperty() {
        String propertyName = DurianPlugins.PROPERTY_PREFIX + ConfigurablePlugin.class.getCanonicalName();
        System.setProperty(propertyName, ConfiguredPlugin.class.getName());

        try {
            ConfigurablePlugin plugin = DurianPlugins.get(ConfigurablePlugin.class, new DefaultPlugin());

            assertThat(plugin).isInstanceOf(ConfiguredPlugin.class);
            assertThat(plugin.name()).isEqualTo("configured");
        } finally {
            System.clearProperty(propertyName);
        }
    }

    public interface ConfigurablePlugin {
        String name();
    }

    public static final class ConfiguredPlugin implements ConfigurablePlugin {
        @Override
        public String name() {
            return "configured";
        }
    }

    private static final class DefaultPlugin implements ConfigurablePlugin {
        @Override
        public String name() {
            return "default";
        }
    }
}
