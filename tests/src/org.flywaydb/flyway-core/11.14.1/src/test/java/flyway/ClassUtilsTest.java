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
import org.flywaydb.core.api.migration.baseline.BaselineMigrationConfigurationExtension;
import org.flywaydb.core.extensibility.ConfigurationExtension;
import org.flywaydb.core.extensibility.Plugin;
import org.flywaydb.core.internal.util.ClassUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilsTest {

    @Test
    void probesClassPresenceAndServiceImplementationsWithTheConfiguredClassLoader() {
        ClassLoader classLoader = getClass().getClassLoader();

        assertThat(ClassUtils.isPresent(ClassicConfiguration.class.getName(), classLoader)).isTrue();
        assertThat(ClassUtils.isImplementationPresent(Plugin.class.getName(), classLoader)).isTrue();
    }

    @Test
    void loadsConcreteConfigurationExtensionAndVerifiesItCanBeInstantiated() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();

        Class<? extends ConfigurationExtension> loadedClass = ClassUtils.loadClass(
                ConfigurationExtension.class,
                BaselineMigrationConfigurationExtension.class.getName(),
                classLoader);

        assertThat(loadedClass).isEqualTo(BaselineMigrationConfigurationExtension.class);
    }

    @Test
    void readsStaticFieldValueByClassName() {
        String environmentName = ClassUtils.getStaticFieldValue(
                ClassicConfiguration.class.getName(),
                "TEMP_ENVIRONMENT_NAME",
                getClass().getClassLoader());

        assertThat(environmentName).isEqualTo(ClassicConfiguration.TEMP_ENVIRONMENT_NAME);
    }

    @Test
    void readsAndWritesDeclaredFieldValuesOnConfigurationExtensions() {
        BaselineMigrationConfigurationExtension extension = new BaselineMigrationConfigurationExtension();

        ClassUtils.setFieldValue(extension, "baselineMigrationPrefix", "X");

        assertThat(ClassUtils.getFieldValue(extension, "baselineMigrationPrefix")).isEqualTo("X");
        assertThat(extension.getBaselineMigrationPrefix()).isEqualTo("X");
    }

    @Test
    void discoversAndInvokesGettableFieldsForConfigurationExtensions() {
        BaselineMigrationConfigurationExtension extension = new BaselineMigrationConfigurationExtension();
        extension.setBaselineMigrationPrefix("V");

        List<String> fields = ClassUtils.getGettableField(extension, "baseline.");
        Map<String, String> values = ClassUtils.getGettableFieldValues(extension, "baseline.");

        assertThat(fields).contains("baseline.baselineMigrationPrefix");
        assertThat(values).containsEntry("baseline.baselineMigrationPrefix", "V");
    }
}
