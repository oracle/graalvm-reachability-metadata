/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_core;

import com.diffplug.common.base.DurianPlugins;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DurianPluginsTest {
    @Test
    public void loadsPluginImplementationConfiguredBySystemProperty() {
        String propertyName = DurianPlugins.PROPERTY_PREFIX + PluginContract.class.getCanonicalName();
        String previousValue = System.getProperty(propertyName);

        try {
            System.setProperty(propertyName, PropertyPluginImplementation.class.getName());

            try {
                PluginContract defaultPlugin = new DefaultPluginImplementation();
                PluginContract plugin = DurianPlugins.get(PluginContract.class, defaultPlugin);

                assertThat(plugin).isInstanceOf(PropertyPluginImplementation.class);
                assertThat(plugin.name()).isEqualTo("system-property");
                assertThat(DurianPlugins.get(PluginContract.class, new DefaultPluginImplementation())).isSameAs(plugin);
            } catch (Error error) {
                if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                    throw error;
                }
            }
        } finally {
            restoreSystemProperty(propertyName, previousValue);
        }
    }

    private static void restoreSystemProperty(String propertyName, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, previousValue);
        }
    }

    public interface PluginContract {
        String name();
    }

    public static final class PropertyPluginImplementation implements PluginContract {
        public PropertyPluginImplementation() {
        }

        @Override
        public String name() {
            return "system-property";
        }
    }

    private static final class DefaultPluginImplementation implements PluginContract {
        @Override
        public String name() {
            return "default";
        }
    }
}
