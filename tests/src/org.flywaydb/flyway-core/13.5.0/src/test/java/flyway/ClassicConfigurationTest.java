/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.util.Map;

import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.flywaydb.core.extensibility.ConfigurationExtension;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassicConfigurationTest {

    @Test
    void configureAppliesNestedConfigurationExtensionProperties() {
        ClassicConfiguration configuration = new ClassicConfiguration();

        configuration.configure(Map.of("flyway.testExtension.settings.child.name", "release-script"));

        NestedConfigurationExtension extension = configuration.getConfigurationExtension(
                NestedConfigurationExtension.class);

        assertThat(extension).isNotNull();
        assertThat(extension.getSettings().getChild().getName()).isEqualTo("release-script");
    }

    public static class NestedConfigurationExtension implements ConfigurationExtension {
        private NestedSettings settings = new NestedSettings();

        public NestedConfigurationExtension() {
        }

        @Override
        public String getNamespace() {
            return "testExtension";
        }

        @Override
        public String getConfigurationParameterFromEnvironmentVariable(final String environmentVariable) {
            return null;
        }

        public NestedSettings getSettings() {
            return settings;
        }

        public void setSettings(final NestedSettings settings) {
            this.settings = settings;
        }
    }

    public static class NestedSettings {
        private ChildSettings child = new ChildSettings();

        public NestedSettings() {
        }

        public ChildSettings getChild() {
            return child;
        }

        public void setChild(final ChildSettings child) {
            this.child = child;
        }
    }

    public static class ChildSettings {
        private String name;

        public ChildSettings() {
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }
    }
}
