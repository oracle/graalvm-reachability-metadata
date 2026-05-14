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
import org.flywaydb.core.internal.configuration.ConfigUtils;
import org.flywaydb.core.internal.configuration.models.FlywayModel;
import org.flywaydb.core.internal.util.ClassUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigUtilsTest {

    @Test
    void possibleConfigurationSuggestionsIncludeExtensionFields() {
        List<String> possibleConfigurations = ConfigUtils.getPossibleFlywayConfigurations(
                "undoFilename",
                FlywayModel.defaults(),
                "flyway.");

        assertThat(possibleConfigurations).contains("prepare.undoFilename");
    }

    @Test
    void classicConfigurationCopiesExtensionFilenameProperty() {
        ClassicConfiguration configuration = new ClassicConfiguration();
        configuration.configure(Map.of("flyway.prepare.undoFilename", "U__undo.sql"));

        ClassicConfiguration copiedConfiguration = new ClassicConfiguration(configuration);
        PrepareConfigurationExtension extension = copiedConfiguration.getConfigurationExtension(
                PrepareConfigurationExtension.class);

        assertThat(extension.getUndoFilename()).isEqualTo("U__undo.sql");
        assertThat(ClassUtils.getGettableFieldValues(extension, "flyway.prepare."))
                .containsEntry("flyway.prepare.undoFilename", "U__undo.sql");
    }

    public static class PrepareConfigurationExtension implements ConfigurationExtension {
        private String undoFilename;

        public PrepareConfigurationExtension() {
        }

        @Override
        public String getNamespace() {
            return "prepare";
        }

        @Override
        public String getConfigurationParameterFromEnvironmentVariable(final String environmentVariable) {
            return null;
        }

        public String getUndoFilename() {
            return undoFilename;
        }

        public void setUndoFilename(final String undoFilename) {
            this.undoFilename = undoFilename;
        }
    }
}
