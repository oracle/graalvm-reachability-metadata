/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.config.plugins.processor.PluginEntry;
import org.apache.logging.log4j.core.config.plugins.util.PluginBuilder;
import org.apache.logging.log4j.core.config.plugins.util.PluginType;
import org.apache.logging.log4j.core.util.Builder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class PluginBuilderTest {
    @Test
    @Timeout(20)
    void buildsPluginWithBuilderFactoryAndInjectedFields() {
        final PluginType<BuilderBackedPlugin> pluginType = pluginType(BuilderBackedPlugin.class, "BuilderBacked");
        final Node node = new Node(null, "BuilderBacked", pluginType);
        node.getAttributes().put("name", "configured-builder");
        node.getAttributes().put("count", "7");

        final Object plugin = new PluginBuilder(pluginType)
                .withConfiguration(new DefaultConfiguration())
                .withConfigurationNode(node)
                .build();

        assertThat(plugin).isInstanceOf(BuilderBackedPlugin.class);
        final BuilderBackedPlugin builtPlugin = (BuilderBackedPlugin) plugin;
        assertThat(builtPlugin.name).isEqualTo("configured-builder");
        assertThat(builtPlugin.count).isEqualTo(7);
        assertThat(node.getAttributes()).isEmpty();
    }

    @Test
    @Timeout(20)
    void fallsBackToAnnotatedFactoryMethodWhenNoBuilderFactoryExists() {
        final PluginType<FactoryBackedPlugin> pluginType = pluginType(FactoryBackedPlugin.class, "FactoryBacked");
        final Node node = new Node(null, "FactoryBacked", pluginType);
        node.getAttributes().put("name", "configured-factory");
        node.getAttributes().put("enabled", "true");

        final Object plugin = new PluginBuilder(pluginType)
                .withConfiguration(new DefaultConfiguration())
                .withConfigurationNode(node)
                .build();

        assertThat(plugin).isInstanceOf(FactoryBackedPlugin.class);
        final FactoryBackedPlugin builtPlugin = (FactoryBackedPlugin) plugin;
        assertThat(builtPlugin.name).isEqualTo("configured-factory");
        assertThat(builtPlugin.enabled).isTrue();
        assertThat(node.getAttributes()).isEmpty();
    }

    private static <T> PluginType<T> pluginType(final Class<T> pluginClass, final String name) {
        final PluginEntry entry = new PluginEntry();
        entry.setKey(name.toLowerCase(Locale.ROOT));
        entry.setName(name);
        entry.setClassName(pluginClass.getName());
        entry.setCategory(Node.CATEGORY);
        return new PluginType<>(entry, pluginClass, name);
    }

    public static final class BuilderBackedPlugin {
        private final String name;
        private final int count;

        private BuilderBackedPlugin(final String name, final int count) {
            this.name = name;
            this.count = count;
        }

        @PluginBuilderFactory
        public static BuilderBackedPluginBuilder newBuilder() {
            return new BuilderBackedPluginBuilder();
        }
    }

    public static final class BuilderBackedPluginBuilder implements Builder<BuilderBackedPlugin> {
        @PluginAttribute("name")
        private String name;

        @PluginAttribute("count")
        private int count;

        @Override
        public BuilderBackedPlugin build() {
            return new BuilderBackedPlugin(name, count);
        }
    }

    public static final class FactoryBackedPlugin {
        private final String name;
        private final boolean enabled;

        private FactoryBackedPlugin(final String name, final boolean enabled) {
            this.name = name;
            this.enabled = enabled;
        }

        @PluginFactory
        public static FactoryBackedPlugin create(
                @PluginAttribute("name") final String name,
                @PluginAttribute("enabled") final boolean enabled) {
            return new FactoryBackedPlugin(name, enabled);
        }
    }
}
