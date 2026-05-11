/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.lang.reflect.Method;
import java.util.List;

import org.flywaydb.core.internal.configuration.ConfigUtils;
import org.flywaydb.core.internal.configuration.models.FlywayEnvironmentModel;
import org.flywaydb.core.internal.configuration.models.FlywayModel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigUtilsTest {

    @Test
    void suggestsConfigurationExtensionFieldsAsPossibleConfigurations() throws ReflectiveOperationException {
        Assumptions.assumeTrue(isClassPresent("org.apache.commons.text.similarity.FuzzyScore"));
        List<String> suggestions = getPossibleFlywayConfigurations("baselineMigrationPrefix", FlywayModel.defaults());

        assertThat(suggestions).anyMatch(suggestion -> suggestion.endsWith("baselineMigrationPrefix"));
    }

    @SuppressWarnings("unchecked")
    private List<String> getPossibleFlywayConfigurations(
            final String configurationName,
            final FlywayEnvironmentModel environmentModel) throws ReflectiveOperationException {
        try {
            Method method = ConfigUtils.class.getMethod(
                    "getPossibleFlywayConfigurations",
                    String.class,
                    FlywayEnvironmentModel.class,
                    String.class);
            return (List<String>) method.invoke(null, configurationName, environmentModel, "flyway.");
        } catch (NoSuchMethodException ignored) {
            Method method = ConfigUtils.class.getMethod(
                    "getPossibleFlywayConfigurations",
                    String.class,
                    FlywayEnvironmentModel.class);
            return (List<String>) method.invoke(null, configurationName, environmentModel);
        }
    }

    private boolean isClassPresent(final String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
