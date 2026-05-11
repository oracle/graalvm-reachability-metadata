/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.util.List;
import java.util.Map;

import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.flywaydb.core.extensibility.ConfigurationExtension;
import org.flywaydb.core.extensibility.Plugin;
import org.flywaydb.core.internal.plugin.PluginRegister;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassicConfigurationTest {

    @Test
    void configuresNestedExtensionProperties() {
        TestConfigurationExtension extension = new TestConfigurationExtension();
        ClassicConfiguration configuration = new ClassicConfiguration();
        configuration.setPluginRegister(new SingleExtensionPluginRegister(extension));

        configuration.configure(Map.of("flyway.test.nested.child.name", "configured"));

        assertThat(extension.getNested()).isNotNull();
        assertThat(extension.getNested().getChild()).isNotNull();
        assertThat(extension.getNested().getChild().getName()).isEqualTo("configured");
    }

    public static class SingleExtensionPluginRegister extends PluginRegister {
        private final ConfigurationExtension extension;

        public SingleExtensionPluginRegister(final ConfigurationExtension extension) {
            this.extension = extension;
        }

        @Override
        public <T extends Plugin> List<T> getInstancesOf(final Class<T> clazz) {
            if (clazz == ConfigurationExtension.class) {
                return List.of(clazz.cast(extension));
            }
            return List.of();
        }
    }

    public static class TestConfigurationExtension implements ConfigurationExtension {
        private Nested nested;

        @Override
        public String getNamespace() {
            return "test";
        }

        @Override
        public String getConfigurationParameterFromEnvironmentVariable(final String environmentVariable) {
            return null;
        }

        public Nested getNested() {
            return nested;
        }

        public void setNested(final Nested nested) {
            this.nested = nested;
        }
    }

    public static class Nested {
        private Child child;

        public Child getChild() {
            return child;
        }

        public void setChild(final Child child) {
            this.child = child;
        }
    }

    public static class Child {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }
    }
}
