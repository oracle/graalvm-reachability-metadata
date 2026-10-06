/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_flywaydb.flyway_core;

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
    void checksPresentClassesAndRegisteredImplementations() {
        ClassLoader classLoader = getClass().getClassLoader();

        assertThat(ClassUtils.isPresent(ClassicConfiguration.class.getName(), classLoader)).isTrue();
        assertThat(ClassUtils.isImplementationPresent(Plugin.class.getName(), classLoader)).isTrue();
    }

    @Test
    void loadsAndInstantiatesConfigurationExtension() throws Exception {
        Class<? extends ConfigurationExtension> loadedClass = ClassUtils.loadClass(
                ConfigurationExtension.class,
                BaselineMigrationConfigurationExtension.class.getName(),
                getClass().getClassLoader());

        assertThat(loadedClass).isEqualTo(BaselineMigrationConfigurationExtension.class);
    }

    @Test
    void readsPublicStaticConfigurationValue() {
        String environmentName = ClassUtils.getStaticFieldValue(
                ClassicConfiguration.class.getName(),
                "TEMP_ENVIRONMENT_NAME",
                getClass().getClassLoader());

        assertThat(environmentName).isEqualTo(ClassicConfiguration.TEMP_ENVIRONMENT_NAME);
    }

    @Test
    void readsWritesAndMapsConfigurationExtensionProperties() {
        BaselineMigrationConfigurationExtension extension = new BaselineMigrationConfigurationExtension();

        ClassUtils.setFieldValue(extension, "baselineMigrationPrefix", "X");

        assertThat(ClassUtils.getFieldValue(extension, "baselineMigrationPrefix")).isEqualTo("X");

        Map<String, String> values = ClassUtils.getGettableFieldValues(extension, "baseline.");
        assertThat(values).containsEntry("baseline.baselineMigrationPrefix", "X");
    }
}
