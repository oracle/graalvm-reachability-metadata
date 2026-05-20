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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassicConfigurationTest {

    @Test
    void configuresNestedExtensionPropertiesFromDeprecatedPluginNamespace() throws ReflectiveOperationException {
        ClassicConfiguration configuration = new ClassicConfiguration();

        Class<? extends ConfigurationExtension> cleanModeExtensionClass = cleanModeConfigurationExtensionClass();
        Assumptions.assumeTrue(cleanModeExtensionClass != null);

        configuration.configure(Map.of("flyway.plugins.clean.mode", "schema"));

        Object cleanModeExtension = configuration.getConfigurationExtension(cleanModeExtensionClass);
        Object clean = cleanModeExtension.getClass().getMethod("getClean").invoke(cleanModeExtension);
        Object mode = clean.getClass().getMethod("getMode").invoke(clean);

        assertThat(cleanModeExtension).isNotNull();
        assertThat(clean).isNotNull();
        assertThat(mode).hasToString("SCHEMA");
    }

    private Class<? extends ConfigurationExtension> cleanModeConfigurationExtensionClass() {
        try {
            return Class.forName("org.flywaydb.core.internal.command.clean.CleanModeConfigurationExtension")
                    .asSubclass(ConfigurationExtension.class);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
