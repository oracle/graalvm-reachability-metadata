/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.PluginLoader;
import com.tngtech.archunit.core.PluginLoader.JavaVersion;
import org.junit.jupiter.api.Test;

public class PluginLoaderInnerPluginSupplierTest {
    @Test
    void loadsConfiguredCompatiblePluginByClassName() {
        GreetingPlugin fallback = () -> "fallback";
        PluginLoader<GreetingPlugin> loader = PluginLoader.forType(GreetingPlugin.class)
                .ifVersionGreaterOrEqualTo(JavaVersion.JAVA_9)
                .load(DefaultGreetingPlugin.class.getName())
                .fallback(fallback);

        GreetingPlugin plugin = loader.load();

        assertThat(plugin).isInstanceOf(DefaultGreetingPlugin.class);
        assertThat(plugin.greeting()).isEqualTo("loaded");
    }

    public interface GreetingPlugin {
        String greeting();
    }

    public static final class DefaultGreetingPlugin implements GreetingPlugin {
        public DefaultGreetingPlugin() {}

        @Override
        public String greeting() {
            return "loaded";
        }
    }
}
