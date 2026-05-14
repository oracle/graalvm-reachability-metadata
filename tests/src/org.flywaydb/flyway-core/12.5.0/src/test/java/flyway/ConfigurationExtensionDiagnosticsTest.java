/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.util.Map;
import java.util.stream.Collectors;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.baseline.BaselineMigrationConfigurationExtension;
import org.flywaydb.core.extensibility.ConfigurationExtension;
import org.flywaydb.core.internal.util.ClassUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigurationExtensionDiagnosticsTest {

    private static final String PREPARE_SCRIPT_FILENAME_CONFIGURATION_EXTENSION =
            "org.flywaydb.core.internal.configuration.extensions.PrepareScriptFilenameConfigurationExtension";

    @Test
    void exposesConfiguredExtensionValuesForDiagnostics() {
        final Flyway flyway = Flyway.configure()
                .configuration(Map.of("flyway.baselineMigrationPrefix", "BL"))
                .load();

        final Map<String, Map<String, String>> valuesByExtension = flyway.getConfiguration()
                .getPluginRegister()
                .getInstancesOf(ConfigurationExtension.class)
                .stream()
                .collect(Collectors.toMap(
                        extension -> extension.getClass().getName(),
                        extension -> ClassUtils.getGettableFieldValues(extension, prefixFor(extension)),
                        (left, right) -> left));

        assertThat(valuesByExtension.get(BaselineMigrationConfigurationExtension.class.getName()))
                .containsEntry("flyway.baselineMigrationPrefix", "BL");

        final Map<String, String> prepareValues = valuesByExtension.get(
                PREPARE_SCRIPT_FILENAME_CONFIGURATION_EXTENSION);
        if (prepareValues != null) {
            assertThat(prepareValues).containsKey("flyway.prepare.undoFilename");
        }
    }

    private static String prefixFor(final ConfigurationExtension extension) {
        final String namespace = extension.getNamespace();
        if (namespace == null || namespace.isEmpty()) {
            return "flyway.";
        }
        return "flyway." + namespace + ".";
    }
}
