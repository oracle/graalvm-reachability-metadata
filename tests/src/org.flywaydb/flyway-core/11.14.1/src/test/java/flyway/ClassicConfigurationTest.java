/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.util.Map;

import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.flywaydb.core.internal.command.clean.CleanModeConfigurationExtension;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassicConfigurationTest {

    @Test
    void configuresNestedExtensionPropertiesFromDeprecatedPluginNamespace() {
        ClassicConfiguration configuration = new ClassicConfiguration();

        configuration.configure(Map.of("flyway.plugins.clean.mode", "schema"));

        CleanModeConfigurationExtension cleanModeExtension = configuration.getConfigurationExtension(
                CleanModeConfigurationExtension.class);
        assertThat(cleanModeExtension).isNotNull();
        assertThat(cleanModeExtension.getClean()).isNotNull();
        assertThat(cleanModeExtension.getClean().getMode()).isEqualTo("SCHEMA");
    }
}
