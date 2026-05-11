/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.util.List;

import org.flywaydb.core.internal.configuration.ConfigUtils;
import org.flywaydb.core.internal.configuration.models.FlywayModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigUtilsTest {

    @Test
    void suggestsConfigurationExtensionFieldsAsPossibleConfigurations() {
        List<String> suggestions = ConfigUtils.getPossibleFlywayConfigurations(
                "baselineMigrationPrefix",
                FlywayModel.defaults(),
                "flyway.");

        assertThat(suggestions).anyMatch(suggestion -> suggestion.endsWith("baselineMigrationPrefix"));
    }
}
