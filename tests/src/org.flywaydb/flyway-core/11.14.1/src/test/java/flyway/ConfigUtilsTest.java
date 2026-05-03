/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import java.lang.reflect.InvocationTargetException;
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
    void suggestsPluginNamespaceForConfigurationExtensionField() throws ReflectiveOperationException {
        Assumptions.assumeTrue(
                isClassPresent("org.apache.commons.text.similarity.FuzzyScore"),
                "ConfigUtils suggestions require commons-text on this Flyway version");

        final List<String> possibleConfigurations = getPossibleFlywayConfigurations("clean");

        assertThat(possibleConfigurations).contains("plugins.clean");
    }

    @SuppressWarnings("unchecked")
    private static List<String> getPossibleFlywayConfigurations(String configuration)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        final FlywayModel defaults = FlywayModel.defaults();

        try {
            final Method threeArgumentMethod = ConfigUtils.class.getMethod(
                    "getPossibleFlywayConfigurations",
                    String.class,
                    FlywayEnvironmentModel.class,
                    String.class);
            return (List<String>) threeArgumentMethod.invoke(null, configuration, defaults, "flyway.");
        } catch (NoSuchMethodException ignored) {
            final Method twoArgumentMethod = ConfigUtils.class.getMethod(
                    "getPossibleFlywayConfigurations",
                    String.class,
                    FlywayEnvironmentModel.class);
            return (List<String>) twoArgumentMethod.invoke(null, configuration, defaults);
        }
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
